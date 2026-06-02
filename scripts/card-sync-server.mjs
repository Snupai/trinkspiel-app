#!/usr/bin/env node
import crypto from "node:crypto";
import fs from "node:fs/promises";
import http from "node:http";
import path from "node:path";
import process from "node:process";

const DEFAULT_DATA_DIR = path.join(process.cwd(), "build", "card-sync-server");
const DEFAULT_DEV_TOKEN = "dev-token:*:admin";
const GENERATED_TOKENS_FILE = "generated-tokens.json";

function parseArgs(argv) {
  const args = {
    host: process.env.SEEMOPS_CARD_SYNC_HOST || "127.0.0.1",
    port: Number(process.env.SEEMOPS_CARD_SYNC_PORT || "8080"),
    dataDir: process.env.SEEMOPS_CARD_SYNC_DATA_DIR || DEFAULT_DATA_DIR,
    tokenSpec: process.env.SEEMOPS_CARD_SYNC_TOKENS || DEFAULT_DEV_TOKEN,
    publicBaseUrl: process.env.SEEMOPS_CARD_SYNC_PUBLIC_BASE_URL || "",
  };
  for (let i = 0; i < argv.length; i += 1) {
    const value = argv[i];
    if (value === "--host") args.host = argv[++i];
    else if (value === "--port") args.port = Number(argv[++i]);
    else if (value === "--data-dir") args.dataDir = argv[++i];
    else if (value === "--tokens") args.tokenSpec = argv[++i];
    else if (value === "--public-base-url") args.publicBaseUrl = argv[++i];
    else if (value === "--help") {
      printHelp();
      process.exit(0);
    }
  }
  if (!Number.isInteger(args.port) || args.port < 0 || args.port > 65535) {
    throw new Error("Invalid --port value");
  }
  return args;
}

function printHelp() {
  console.log(`
Usage: node scripts/card-sync-server.mjs [--host 127.0.0.1] [--port 8080] [--data-dir build/card-sync-server] [--tokens token:library:role[:contributorUserId[:contributorName]],...] [--public-base-url http://127.0.0.1:8080]

Token roles:
  read   can fetch cards for the library
  write  can fetch and batch-upsert review-pending new cards or own contributed cards
  admin  can fetch, batch-upsert, approve, and delete cards for the library

Admins can also create contributor invites with POST /libraries/{library}/invites.
Admins can inspect configured/generated memberships with GET /libraries/{library}/memberships.
Admins can revoke generated invite memberships with DELETE /libraries/{library}/memberships/{tokenId}.

Use "*" as library for all libraries. URL-encode contributorName if it contains spaces, commas, or colons. If no token spec is provided, local dev uses: ${DEFAULT_DEV_TOKEN}
`.trim());
}

function parseTokens(tokenSpec) {
  return tokenSpec
    .split(",")
    .map((part) => part.trim())
    .filter(Boolean)
    .map((part) => {
      const [token, libraryOwnerUserId = "*", role = "read", contributorUserId, ...nameParts] =
        part.split(":");
      if (!token) throw new Error(`Invalid token spec: ${part}`);
      const cleanRole = role.toLowerCase();
      if (!["read", "write", "admin"].includes(cleanRole)) {
        throw new Error(`Invalid token role: ${role}`);
      }
      const cleanContributorUserId = contributorUserId?.trim() || null;
      const contributorNameRaw = nameParts.join(":").trim();
      const cleanContributorName = contributorNameRaw
        ? decodeURIComponent(contributorNameRaw)
        : cleanContributorUserId;
      return {
        token,
        libraryOwnerUserId,
        role: cleanRole,
        contributorUserId: cleanContributorUserId,
        contributorName: cleanContributorName,
        source: "configured",
      };
    });
}

async function loadGeneratedTokens(dataDir) {
  const file = path.join(dataDir, GENERATED_TOKENS_FILE);
  try {
    const raw = await fs.readFile(file, "utf8");
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed.tokens)) return [];
    return parsed.tokens
      .map((entry) => sanitizeGeneratedToken(entry))
      .filter(Boolean);
  } catch (error) {
    if (error.code === "ENOENT") return [];
    throw error;
  }
}

async function saveGeneratedTokens(dataDir, generatedTokens) {
  await fs.mkdir(dataDir, { recursive: true });
  const file = path.join(dataDir, GENERATED_TOKENS_FILE);
  const payload = {
    version: 1,
    updatedAtMillis: Date.now(),
    tokens: generatedTokens
      .slice()
      .sort((a, b) => String(a.createdAtMillis || 0).localeCompare(String(b.createdAtMillis || 0))),
  };
  await fs.writeFile(file, JSON.stringify(payload, null, 2) + "\n", "utf8");
}

function sanitizeGeneratedToken(entry) {
  const token = String(entry?.token || "").trim();
  const libraryOwnerUserId = String(entry?.libraryOwnerUserId || "").trim();
  const role = String(entry?.role || "read").trim().toLowerCase();
  if (!token || !libraryOwnerUserId || !["read", "write", "admin"].includes(role)) return null;
  const contributorUserId = String(entry?.contributorUserId || "").trim() || null;
  const contributorName = String(entry?.contributorName || contributorUserId || "").trim() || null;
  return {
    token,
    libraryOwnerUserId,
    role,
    contributorUserId,
    contributorName,
    createdAtMillis: Number(entry?.createdAtMillis || 0),
    createdByUserId: String(entry?.createdByUserId || "").trim() || null,
    source: "generated",
  };
}

function membershipFor(req, tokens) {
  const authorization = req.headers.authorization || "";
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  if (!match) return null;
  return tokens.find((entry) => entry.token === match[1]) || null;
}

function canAccess(membership, ownerUserId, mode) {
  if (!membership) return false;
  const sameLibrary =
    membership.libraryOwnerUserId === "*" || membership.libraryOwnerUserId === ownerUserId;
  if (!sameLibrary) return false;
  if (mode === "read") return ["read", "write", "admin"].includes(membership.role);
  if (mode === "admin") return membership.role === "admin";
  return ["write", "admin"].includes(membership.role);
}

function canUpdateExistingCard(membership, existingCard) {
  if (!existingCard) return true;
  if (membership.role === "admin") return true;
  if (!membership.contributorUserId) return true;
  return String(existingCard.contributorUserId || "").trim() === membership.contributorUserId;
}

function tokenIdFor(token) {
  return crypto.createHash("sha256").update(token).digest("hex").slice(0, 16);
}

function membershipSummaries(tokens, ownerUserId) {
  return tokens
    .filter((entry) => entry.libraryOwnerUserId === "*" || entry.libraryOwnerUserId === ownerUserId)
    .map((entry) => ({
      tokenId: tokenIdFor(entry.token),
      libraryOwnerUserId:
        entry.libraryOwnerUserId === "*" ? ownerUserId : entry.libraryOwnerUserId,
      role: entry.role,
      contributorUserId: entry.contributorUserId || "",
      contributorName: entry.contributorName || "",
      source: entry.source || "configured",
      createdAtMillis: Number(entry.createdAtMillis || 0),
    }))
    .sort((a, b) => {
      const roleOrder = { admin: 0, write: 1, read: 2 };
      return (
        (roleOrder[a.role] ?? 9) - (roleOrder[b.role] ?? 9) ||
        a.contributorName.localeCompare(b.contributorName) ||
        a.tokenId.localeCompare(b.tokenId)
      );
    });
}

function removeGeneratedMembership(tokens, generatedTokens, ownerUserId, tokenId) {
  const cleanTokenId = String(tokenId || "").trim();
  if (!cleanTokenId) return null;
  const index = generatedTokens.findIndex(
    (entry) =>
      entry.libraryOwnerUserId === ownerUserId &&
      tokenIdFor(entry.token) === cleanTokenId
  );
  if (index < 0) return null;
  const [removed] = generatedTokens.splice(index, 1);
  const tokenIndex = tokens.findIndex((entry) => entry.token === removed.token);
  if (tokenIndex >= 0) tokens.splice(tokenIndex, 1);
  return removed;
}

function httpError(statusCode, message) {
  const error = new Error(message);
  error.statusCode = statusCode;
  return error;
}

function normalizeInviteRole(value) {
  const role = String(value || "write").trim().toLowerCase();
  if (!["read", "write", "admin"].includes(role)) {
    throw new Error("Invite role must be read, write, or admin");
  }
  return role;
}

function normalizeDisplayName(value, fallback = "Beiträger") {
  return String(value || "").trim() || fallback;
}

function slugForName(value) {
  const slug = String(value || "")
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .slice(0, 40);
  return slug || "contributor";
}

function generatedContributorUserId(contributorName) {
  return `account_${slugForName(contributorName)}_${crypto.randomBytes(4).toString("hex")}`;
}

function requestBaseUrl(req, publicBaseUrl) {
  const configured = String(publicBaseUrl || "").trim().replace(/\/+$/, "");
  if (configured) return configured;
  const forwardedProto = String(req.headers["x-forwarded-proto"] || "").split(",")[0].trim();
  const forwardedHost = String(req.headers["x-forwarded-host"] || "").split(",")[0].trim();
  const proto = forwardedProto || (req.socket.encrypted ? "https" : "http");
  const host = forwardedHost || req.headers.host || "127.0.0.1";
  return `${proto}://${host}`.replace(/\/+$/, "");
}

function fileNameForOwner(ownerUserId) {
  return Buffer.from(ownerUserId, "utf8").toString("base64url") + ".json";
}

async function loadCards(dataDir, ownerUserId) {
  const file = path.join(dataDir, "libraries", fileNameForOwner(ownerUserId));
  try {
    const raw = await fs.readFile(file, "utf8");
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed.cards) ? parsed.cards : [];
  } catch (error) {
    if (error.code === "ENOENT") return [];
    throw error;
  }
}

async function saveCards(dataDir, ownerUserId, cards) {
  const dir = path.join(dataDir, "libraries");
  await fs.mkdir(dir, { recursive: true });
  const file = path.join(dir, fileNameForOwner(ownerUserId));
  const payload = {
    ownerUserId,
    updatedAtMillis: Date.now(),
    cards: cards
      .slice()
      .sort((a, b) => String(a.remoteId).localeCompare(String(b.remoteId))),
  };
  await fs.writeFile(file, JSON.stringify(payload, null, 2) + "\n", "utf8");
}

function sanitizeCard(card, ownerUserId, now, membership, existingCard = null) {
  const text = String(card.text || "").trim();
  if (!text) throw new Error("Card text is required");
  const drinks = Math.max(1, Number.parseInt(card.drinks, 10) || 1);
  const ownerName = String(card.ownerName || ownerUserId).trim() || ownerUserId;
  const existingContributorUserId = String(existingCard?.contributorUserId || "").trim();
  const existingContributorName = String(existingCard?.contributorName || "").trim();
  const contributorUserId =
    existingContributorUserId ||
    membership.contributorUserId ||
    String(card.contributorUserId || ownerUserId).trim() ||
    ownerUserId;
  const contributorName =
    existingContributorName ||
    membership.contributorName ||
    String(card.contributorName || ownerName).trim() ||
    ownerName;
  const questionLevel = [1, 2, 3].includes(Number(card.questionLevel))
    ? Number(card.questionLevel)
    : drinks <= 1
      ? 1
      : drinks <= 3
        ? 2
        : 3;
  const adminCanModerate = membership.role === "admin";
  const pendingReview = adminCanModerate
    ? Boolean(card.pendingReview ?? card.isPendingReview ?? false)
    : true;
  return {
    remoteId: String(card.remoteId || `server-${crypto.randomUUID()}`).trim(),
    text,
    drinks,
    category: String(card.category || "challenge").trim() || "challenge",
    packName: String(card.packName || "Eigene Karten").trim() || "Eigene Karten",
    enabled: adminCanModerate ? Boolean(card.enabled ?? card.isEnabled ?? true) : false,
    pendingReview,
    questionLevel,
    ownerUserId,
    ownerName,
    contributorUserId,
    contributorName,
    updatedAtMillis: now,
  };
}

function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    let body = "";
    req.setEncoding("utf8");
    req.on("data", (chunk) => {
      body += chunk;
      if (body.length > 2_000_000) {
        reject(new Error("Request body too large"));
        req.destroy();
      }
    });
    req.on("end", () => {
      try {
        resolve(body.trim() ? JSON.parse(body) : {});
      } catch {
        reject(new Error("Invalid JSON body"));
      }
    });
    req.on("error", reject);
  });
}

function sendJson(res, statusCode, value) {
  const body = JSON.stringify(value, null, 2);
  res.writeHead(statusCode, {
    "Content-Type": "application/json; charset=utf-8",
    "Content-Length": Buffer.byteLength(body),
  });
  res.end(body);
}

function sendError(res, statusCode, message) {
  sendJson(res, statusCode, { error: message });
}

function routeFor(req) {
  const url = new URL(req.url, "http://127.0.0.1");
  const cardsMatch = url.pathname.match(/^\/libraries\/([^/]+)\/cards(?::(?:batchUpsert|batchDelete))?$/);
  if (cardsMatch) {
    return {
      type: url.pathname.endsWith(":batchUpsert")
        ? "batchUpsert"
        : url.pathname.endsWith(":batchDelete")
          ? "batchDelete"
          : "cards",
      ownerUserId: decodeURIComponent(cardsMatch[1]),
    };
  }
  const invitesMatch = url.pathname.match(/^\/libraries\/([^/]+)\/invites$/);
  if (invitesMatch) {
    return {
      type: "invites",
      ownerUserId: decodeURIComponent(invitesMatch[1]),
    };
  }
  const membershipsMatch = url.pathname.match(/^\/libraries\/([^/]+)\/memberships$/);
  if (membershipsMatch) {
    return {
      type: "memberships",
      ownerUserId: decodeURIComponent(membershipsMatch[1]),
    };
  }
  const membershipMatch = url.pathname.match(/^\/libraries\/([^/]+)\/memberships\/([^/]+)$/);
  if (membershipMatch) {
    return {
      type: "membership",
      ownerUserId: decodeURIComponent(membershipMatch[1]),
      tokenId: decodeURIComponent(membershipMatch[2]),
    };
  }
  if (url.pathname === "/health") return { type: "health" };
  return {
    type: "notFound",
  };
}

function createServer({ dataDir, tokens, generatedTokens, publicBaseUrl }) {
  return http.createServer(async (req, res) => {
    try {
      const route = routeFor(req);
      if (route.type === "health") {
        sendJson(res, 200, { ok: true });
        return;
      }
      if (route.type === "notFound") {
        sendError(res, 404, "Not found");
        return;
      }

      const membership = membershipFor(req, tokens);
      if (!membership) {
        sendError(res, 401, "Missing or invalid bearer token");
        return;
      }

      if (route.type === "invites" && req.method === "POST") {
        if (!canAccess(membership, route.ownerUserId, "admin")) {
          sendError(res, 403, "Token cannot create invites for this library");
          return;
        }
        const body = await readJsonBody(req);
        const now = Date.now();
        const role = normalizeInviteRole(body.role);
        const contributorName = normalizeDisplayName(body.contributorName, "Beiträger");
        const contributorUserId =
          String(body.contributorUserId || "").trim() || generatedContributorUserId(contributorName);
        const token = `invite_${crypto.randomBytes(24).toString("base64url")}`;
        const generated = {
          token,
          libraryOwnerUserId: route.ownerUserId,
          role,
          contributorUserId,
          contributorName,
          createdAtMillis: now,
          createdByUserId: membership.contributorUserId || membership.libraryOwnerUserId,
          source: "generated",
        };
        tokens.push(generated);
        generatedTokens.push(generated);
        await saveGeneratedTokens(dataDir, generatedTokens);
        sendJson(res, 201, {
          invite: {
            type: "seemops.backend_sync_invite",
            version: 1,
            createdAtMillis: now,
            endpointUrl: requestBaseUrl(req, publicBaseUrl),
            accessToken: token,
            libraryOwnerUserId: route.ownerUserId,
            libraryOwnerName: normalizeDisplayName(body.libraryOwnerName, route.ownerUserId),
            contributorUserId,
            contributorName,
            role,
          },
        });
        return;
      }

      if (route.type === "memberships" && req.method === "GET") {
        if (!canAccess(membership, route.ownerUserId, "admin")) {
          sendError(res, 403, "Token cannot inspect memberships for this library");
          return;
        }
        sendJson(res, 200, {
          libraryOwnerUserId: route.ownerUserId,
          memberships: membershipSummaries(tokens, route.ownerUserId),
        });
        return;
      }

      if (route.type === "membership" && req.method === "DELETE") {
        if (!canAccess(membership, route.ownerUserId, "admin")) {
          sendError(res, 403, "Token cannot revoke memberships for this library");
          return;
        }
        const removed = removeGeneratedMembership(
          tokens,
          generatedTokens,
          route.ownerUserId,
          route.tokenId
        );
        if (!removed) {
          sendError(res, 404, "Generated membership not found");
          return;
        }
        await saveGeneratedTokens(dataDir, generatedTokens);
        sendJson(res, 200, {
          revoked: {
            tokenId: tokenIdFor(removed.token),
            libraryOwnerUserId: removed.libraryOwnerUserId,
            role: removed.role,
            contributorUserId: removed.contributorUserId || "",
            contributorName: removed.contributorName || "",
            source: "generated",
          },
        });
        return;
      }

      if (route.type === "cards" && req.method === "GET") {
        if (!canAccess(membership, route.ownerUserId, "read")) {
          sendError(res, 403, "Token cannot read this library");
          return;
        }
        sendJson(res, 200, { cards: await loadCards(dataDir, route.ownerUserId) });
        return;
      }

      if (route.type === "batchUpsert" && req.method === "POST") {
        if (!canAccess(membership, route.ownerUserId, "write")) {
          sendError(res, 403, "Token cannot write this library");
          return;
        }
        const body = await readJsonBody(req);
        const incoming = Array.isArray(body.cards) ? body.cards : [];
        const existing = await loadCards(dataDir, route.ownerUserId);
        const byId = new Map(existing.map((card) => [card.remoteId, card]));
        const now = Date.now();
        const results = incoming.map((item) => {
          const incomingCard = item.card || item;
          const requestedRemoteId = String(incomingCard.remoteId || "").trim();
          const existingCard = requestedRemoteId ? byId.get(requestedRemoteId) : null;
          if (!canUpdateExistingCard(membership, existingCard)) {
            throw httpError(403, "Token cannot update cards from another contributor");
          }
          const card = sanitizeCard(incomingCard, route.ownerUserId, now, membership, existingCard);
          byId.set(card.remoteId, card);
          return {
            clientLocalId: Number(item.clientLocalId || 0),
            card,
          };
        });
        await saveCards(dataDir, route.ownerUserId, Array.from(byId.values()));
        sendJson(res, 200, { cards: results });
        return;
      }

      if (route.type === "batchDelete" && req.method === "POST") {
        if (!canAccess(membership, route.ownerUserId, "admin")) {
          sendError(res, 403, "Token cannot delete cards in this library");
          return;
        }
        const body = await readJsonBody(req);
        const requestedRemoteIds = Array.isArray(body.remoteIds)
          ? body.remoteIds.map((remoteId) => String(remoteId || "").trim()).filter(Boolean)
          : [];
        const requested = [...new Set(requestedRemoteIds)];
        const existing = await loadCards(dataDir, route.ownerUserId);
        const requestedSet = new Set(requested);
        const deletedRemoteIds = existing
          .filter((card) => requestedSet.has(String(card.remoteId || "")))
          .map((card) => String(card.remoteId));
        if (deletedRemoteIds.length > 0) {
          const deletedSet = new Set(deletedRemoteIds);
          await saveCards(
            dataDir,
            route.ownerUserId,
            existing.filter((card) => !deletedSet.has(String(card.remoteId || "")))
          );
        }
        sendJson(res, 200, {
          deletedRemoteIds,
          skippedRemoteIds: requested.filter((remoteId) => !deletedRemoteIds.includes(remoteId)),
        });
        return;
      }

      sendError(res, 405, "Method not allowed");
    } catch (error) {
      const statusCode = Number.isInteger(error.statusCode) ? error.statusCode : 400;
      sendError(res, statusCode, error.message || "Bad request");
    }
  });
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  await fs.mkdir(args.dataDir, { recursive: true });
  const generatedTokens = await loadGeneratedTokens(args.dataDir);
  const tokens = [...parseTokens(args.tokenSpec), ...generatedTokens];
  const server = createServer({
    dataDir: args.dataDir,
    tokens,
    generatedTokens,
    publicBaseUrl: args.publicBaseUrl,
  });
  server.listen(args.port, args.host, () => {
    const { port } = server.address();
    console.log(
      JSON.stringify({
        event: "ready",
        baseUrl: `http://${args.host}:${port}`,
        dataDir: args.dataDir,
      })
    );
  });
}

main().catch((error) => {
  console.error(error.message || error);
  process.exit(1);
});
