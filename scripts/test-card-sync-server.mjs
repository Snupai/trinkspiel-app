#!/usr/bin/env node
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { spawn } from "node:child_process";

const repoRoot = path.resolve(import.meta.dirname, "..");
const serverScript = path.join(repoRoot, "scripts", "card-sync-server.mjs");

async function waitForReady(child) {
  return new Promise((resolve, reject) => {
    let stdout = "";
    let stderr = "";
    const timeout = setTimeout(() => {
      reject(new Error(`Server did not become ready. stdout=${stdout} stderr=${stderr}`));
    }, 5_000);
    child.stdout.on("data", (chunk) => {
      stdout += chunk.toString("utf8");
      const line = stdout.split("\n").find((entry) => entry.trim().startsWith("{"));
      if (line) {
        clearTimeout(timeout);
        resolve(JSON.parse(line));
      }
    });
    child.stderr.on("data", (chunk) => {
      stderr += chunk.toString("utf8");
    });
    child.on("exit", (code) => {
      reject(new Error(`Server exited early with ${code}. stdout=${stdout} stderr=${stderr}`));
    });
  });
}

async function request(baseUrl, pathName, options = {}) {
  const response = await fetch(`${baseUrl}${pathName}`, options);
  const text = await response.text();
  const json = text ? JSON.parse(text) : null;
  return { status: response.status, json };
}

async function main() {
  const dataDir = await fs.mkdtemp(path.join(os.tmpdir(), "seemops-card-sync-"));
  const libraryId = "local_user_testlibrary";
  const otherLibraryId = "local_user_other";
  const tokenSpec = [
    `writer:${libraryId}:write:account_mika:Mika%20Server`,
    `sam:${libraryId}:write:account_sam:Sam`,
    `admin:${libraryId}:admin:account_lena:Lena%20Admin`,
    `reader:${libraryId}:read`,
    `other:${otherLibraryId}:write`,
  ].join(",");
  const child = spawn(
    process.execPath,
    [serverScript, "--port", "0", "--data-dir", dataDir, "--tokens", tokenSpec],
    {
      cwd: repoRoot,
      stdio: ["ignore", "pipe", "pipe"],
    }
  );

  try {
    const ready = await waitForReady(child);
    const baseUrl = ready.baseUrl;

    const unauthorized = await request(baseUrl, `/libraries/${libraryId}/cards`);
    assert.equal(unauthorized.status, 401);

    const wrongLibrary = await request(baseUrl, `/libraries/${libraryId}/cards`, {
      headers: { Authorization: "Bearer other" },
    });
    assert.equal(wrongLibrary.status, 403);

    const empty = await request(baseUrl, `/libraries/${libraryId}/cards`, {
      headers: { Authorization: "Bearer reader" },
    });
    assert.equal(empty.status, 200);
    assert.deepEqual(empty.json.cards, []);

    const readOnlyWrite = await request(baseUrl, `/libraries/${libraryId}/cards:batchUpsert`, {
      method: "POST",
      headers: {
        Authorization: "Bearer reader",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ cards: [] }),
    });
    assert.equal(readOnlyWrite.status, 403);

    const created = await request(baseUrl, `/libraries/${libraryId}/cards:batchUpsert`, {
      method: "POST",
      headers: {
        Authorization: "Bearer writer",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        cards: [
          {
            clientLocalId: 42,
            operation: "create",
            card: {
              text: "Mika Vorschlag",
              drinks: 4,
              category: "spicy",
              packName: "WG",
              enabled: false,
              pendingReview: true,
              questionLevel: 3,
              ownerUserId: "malicious-owner",
              ownerName: "WG Bibliothek",
              contributorUserId: "malicious-contributor",
              contributorName: "Fake Mika",
              updatedAtMillis: 1,
            },
          },
        ],
      }),
    });
    assert.equal(created.status, 200);
    assert.equal(created.json.cards.length, 1);
    const createdCard = created.json.cards[0].card;
    assert.equal(created.json.cards[0].clientLocalId, 42);
    assert.match(createdCard.remoteId, /^server-/);
    assert.equal(createdCard.ownerUserId, libraryId);
    assert.equal(createdCard.contributorUserId, "account_mika");
    assert.equal(createdCard.contributorName, "Mika Server");
    assert.equal(createdCard.pendingReview, true);
    assert.equal(createdCard.questionLevel, 3);
    assert.ok(createdCard.updatedAtMillis > 1);

    const fetched = await request(baseUrl, `/libraries/${libraryId}/cards`, {
      headers: { Authorization: "Bearer writer" },
    });
    assert.equal(fetched.status, 200);
    assert.equal(fetched.json.cards.length, 1);
    assert.equal(fetched.json.cards[0].remoteId, createdCard.remoteId);
    assert.equal(fetched.json.cards[0].text, "Mika Vorschlag");

    const blockedUpdate = await request(baseUrl, `/libraries/${libraryId}/cards:batchUpsert`, {
      method: "POST",
      headers: {
        Authorization: "Bearer sam",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        cards: [
          {
            clientLocalId: 43,
            operation: "update",
            card: {
              ...createdCard,
              text: "Sam überschreibt Mika",
            },
          },
        ],
      }),
    });
    assert.equal(blockedUpdate.status, 403);

    const adminUpdate = await request(baseUrl, `/libraries/${libraryId}/cards:batchUpsert`, {
      method: "POST",
      headers: {
        Authorization: "Bearer admin",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        cards: [
          {
            clientLocalId: 44,
            operation: "update",
            card: {
              ...createdCard,
              text: "Admin Review",
              enabled: true,
              pendingReview: false,
              contributorUserId: "malicious-admin-contributor",
              contributorName: "Fake Admin",
            },
          },
        ],
      }),
    });
    assert.equal(adminUpdate.status, 200);
    assert.equal(adminUpdate.json.cards[0].card.text, "Admin Review");
    assert.equal(adminUpdate.json.cards[0].card.contributorUserId, "account_mika");
    assert.equal(adminUpdate.json.cards[0].card.contributorName, "Mika Server");
    assert.equal(adminUpdate.json.cards[0].card.enabled, true);
    assert.equal(adminUpdate.json.cards[0].card.pendingReview, false);

    const contributorEditAfterApproval = await request(baseUrl, `/libraries/${libraryId}/cards:batchUpsert`, {
      method: "POST",
      headers: {
        Authorization: "Bearer writer",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        cards: [
          {
            clientLocalId: 46,
            operation: "update",
            card: {
              ...adminUpdate.json.cards[0].card,
              text: "Mika Bearbeitung nach Freigabe",
              enabled: true,
              pendingReview: false,
            },
          },
        ],
      }),
    });
    assert.equal(contributorEditAfterApproval.status, 200);
    assert.equal(contributorEditAfterApproval.json.cards[0].card.text, "Mika Bearbeitung nach Freigabe");
    assert.equal(contributorEditAfterApproval.json.cards[0].card.contributorUserId, "account_mika");
    assert.equal(contributorEditAfterApproval.json.cards[0].card.enabled, false);
    assert.equal(contributorEditAfterApproval.json.cards[0].card.pendingReview, true);

    const writerMemberships = await request(baseUrl, `/libraries/${libraryId}/memberships`, {
      headers: { Authorization: "Bearer writer" },
    });
    assert.equal(writerMemberships.status, 403);

    const writerInvite = await request(baseUrl, `/libraries/${libraryId}/invites`, {
      method: "POST",
      headers: {
        Authorization: "Bearer writer",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        libraryOwnerName: "Testbibliothek",
        contributorName: "Nora",
        role: "write",
      }),
    });
    assert.equal(writerInvite.status, 403);

    const invite = await request(baseUrl, `/libraries/${libraryId}/invites`, {
      method: "POST",
      headers: {
        Authorization: "Bearer admin",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        libraryOwnerName: "Testbibliothek",
        contributorName: "Nora",
        role: "write",
      }),
    });
    assert.equal(invite.status, 201);
    assert.equal(invite.json.invite.type, "seemops.backend_sync_invite");
    assert.match(invite.json.invite.accessToken, /^invite_/);
    assert.equal(invite.json.invite.libraryOwnerUserId, libraryId);
    assert.equal(invite.json.invite.libraryOwnerName, "Testbibliothek");
    assert.match(invite.json.invite.contributorUserId, /^account_nora_/);
    assert.equal(invite.json.invite.contributorName, "Nora");
    assert.equal(invite.json.invite.role, "write");

    const invitedCreate = await request(baseUrl, `/libraries/${libraryId}/cards:batchUpsert`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${invite.json.invite.accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        cards: [
          {
            clientLocalId: 45,
            operation: "create",
            card: {
              text: "Nora Vorschlag",
              drinks: 2,
              category: "challenge",
              packName: "WG",
              enabled: true,
              pendingReview: false,
              questionLevel: 2,
              contributorUserId: "spoofed",
              contributorName: "Fake Nora",
            },
          },
        ],
      }),
    });
    assert.equal(invitedCreate.status, 200);
    assert.equal(
      invitedCreate.json.cards[0].card.contributorUserId,
      invite.json.invite.contributorUserId
    );
    assert.equal(invitedCreate.json.cards[0].card.contributorName, "Nora");
    assert.equal(invitedCreate.json.cards[0].card.enabled, false);
    assert.equal(invitedCreate.json.cards[0].card.pendingReview, true);

    const writerDelete = await request(baseUrl, `/libraries/${libraryId}/cards:batchDelete`, {
      method: "POST",
      headers: {
        Authorization: "Bearer writer",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ remoteIds: [invitedCreate.json.cards[0].card.remoteId] }),
    });
    assert.equal(writerDelete.status, 403);

    const adminDelete = await request(baseUrl, `/libraries/${libraryId}/cards:batchDelete`, {
      method: "POST",
      headers: {
        Authorization: "Bearer admin",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        remoteIds: [invitedCreate.json.cards[0].card.remoteId, "missing-remote-card"],
      }),
    });
    assert.equal(adminDelete.status, 200);
    assert.deepEqual(adminDelete.json.deletedRemoteIds, [invitedCreate.json.cards[0].card.remoteId]);
    assert.deepEqual(adminDelete.json.skippedRemoteIds, ["missing-remote-card"]);

    const afterAdminDelete = await request(baseUrl, `/libraries/${libraryId}/cards`, {
      headers: { Authorization: "Bearer reader" },
    });
    assert.equal(afterAdminDelete.status, 200);
    assert.ok(
      !afterAdminDelete.json.cards.some(
        (card) => card.remoteId === invitedCreate.json.cards[0].card.remoteId
      )
    );

    const memberships = await request(baseUrl, `/libraries/${libraryId}/memberships`, {
      headers: { Authorization: "Bearer admin" },
    });
    assert.equal(memberships.status, 200);
    assert.equal(memberships.json.libraryOwnerUserId, libraryId);
    assert.ok(
      memberships.json.memberships.some(
        (membership) =>
          membership.role === "admin" &&
          membership.contributorUserId === "account_lena" &&
          membership.source === "configured" &&
          !Object.hasOwn(membership, "token")
      )
    );
    const generatedMembership = memberships.json.memberships.find(
      (membership) =>
        membership.role === "write" &&
        membership.contributorUserId === invite.json.invite.contributorUserId &&
        membership.source === "generated"
    );
    assert.ok(generatedMembership);
    assert.ok(
      generatedMembership.contributorName === "Nora" &&
        generatedMembership.createdAtMillis > 0 &&
        !Object.hasOwn(generatedMembership, "token")
    );

    const generatedTokensBeforeRevoke = JSON.parse(
      await fs.readFile(path.join(dataDir, "generated-tokens.json"), "utf8")
    );
    assert.equal(generatedTokensBeforeRevoke.tokens.length, 1);
    assert.equal(generatedTokensBeforeRevoke.tokens[0].token, invite.json.invite.accessToken);

    const revoked = await request(
      baseUrl,
      `/libraries/${libraryId}/memberships/${generatedMembership.tokenId}`,
      {
        method: "DELETE",
        headers: { Authorization: "Bearer admin" },
      }
    );
    assert.equal(revoked.status, 200);
    assert.equal(revoked.json.revoked.tokenId, generatedMembership.tokenId);
    assert.equal(revoked.json.revoked.contributorUserId, invite.json.invite.contributorUserId);

    const revokedWrite = await request(baseUrl, `/libraries/${libraryId}/cards:batchUpsert`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${invite.json.invite.accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ cards: [] }),
    });
    assert.equal(revokedWrite.status, 401);

    const membershipsAfterRevoke = await request(baseUrl, `/libraries/${libraryId}/memberships`, {
      headers: { Authorization: "Bearer admin" },
    });
    assert.equal(membershipsAfterRevoke.status, 200);
    assert.ok(
      !membershipsAfterRevoke.json.memberships.some(
        (membership) => membership.tokenId === generatedMembership.tokenId
      )
    );

    const generatedTokensAfterRevoke = JSON.parse(
      await fs.readFile(path.join(dataDir, "generated-tokens.json"), "utf8")
    );
    assert.equal(generatedTokensAfterRevoke.tokens.length, 0);
  } finally {
    child.kill("SIGTERM");
    await fs.rm(dataDir, { recursive: true, force: true });
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
