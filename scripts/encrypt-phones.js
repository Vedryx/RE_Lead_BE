/*
 * One-off migration: encrypt the phone numbers already in the database.
 *
 * Run it ONCE, with the same key the application will use, BEFORE starting a
 * build that has PHONE_ENC_KEY set. Leads written before this runs stay in
 * plaintext and would then be invisible to every lookup, because a search
 * encrypts its term before matching.
 *
 * Usage:
 *   mongosh "<connection string>" --eval "var KEY='<base64 key>'" scripts/encrypt-phones.js
 *
 * Safe to re-run: a value that already carries the enc:v1: marker is skipped,
 * so an interrupted run can simply be started again.
 *
 * This mirrors PhoneCipher exactly — AES-256-GCM with the IV derived as
 * HMAC-SHA256(hmac(key,"phone-iv"), plaintext) truncated to 12 bytes. Change
 * one and you must change the other.
 */

const crypto = require("crypto");

if (typeof KEY === "undefined" || !KEY) {
  throw new Error("Pass the key: --eval \"var KEY='<base64>'\"");
}
const aesKey = Buffer.from(KEY, "base64");
if (aesKey.length !== 32) {
  throw new Error(`key must decode to 32 bytes, got ${aesKey.length}`);
}
const macKey = crypto.createHmac("sha256", aesKey).update("phone-iv").digest();
const PREFIX = "enc:v1:";

function encrypt(plaintext) {
  if (plaintext === null || plaintext === undefined) return plaintext;
  const text = String(plaintext);
  if (text === "" || text.startsWith(PREFIX)) return plaintext;
  const plain = Buffer.from(text, "utf8");
  const iv = crypto.createHmac("sha256", macKey).update(plain).digest().subarray(0, 12);
  const cipher = crypto.createCipheriv("aes-256-gcm", aesKey, iv);
  const encrypted = Buffer.concat([cipher.update(plain), cipher.final(), cipher.getAuthTag()]);
  return PREFIX + Buffer.concat([iv, encrypted]).toString("base64");
}

function migrate(collection, fields) {
  let scanned = 0;
  let changed = 0;
  db.getCollection(collection).find({}).forEach((doc) => {
    scanned++;
    const set = {};
    for (const field of fields) {
      const value = doc[field];
      if (typeof value === "string" && value !== "" && !value.startsWith(PREFIX)) {
        set[field] = encrypt(value);
      }
    }
    if (Object.keys(set).length > 0) {
      db.getCollection(collection).updateOne({ _id: doc._id }, { $set: set });
      changed++;
    }
  });
  print(`${collection}: ${scanned} scanned, ${changed} updated`);
}

// The audit trail keeps a lead's old and new numbers as text in changes[].from/to.
function migrateAudit() {
  const phoneFields = ["phone", "callingPhone", "whatsappPhone"];
  let scanned = 0;
  let changed = 0;
  db.getCollection("lead_audit").find({ "changes.field": { $in: phoneFields } }).forEach((doc) => {
    scanned++;
    let touched = false;
    const changes = doc.changes.map((change) => {
      if (!phoneFields.includes(change.field)) return change;
      const from = encrypt(change.from);
      const to = encrypt(change.to);
      if (from !== change.from || to !== change.to) touched = true;
      return Object.assign({}, change, { from: from, to: to });
    });
    if (touched) {
      db.getCollection("lead_audit").updateOne({ _id: doc._id }, { $set: { changes: changes } });
      changed++;
    }
  });
  print(`lead_audit: ${scanned} scanned, ${changed} updated`);
}

migrate("lead", ["phone", "calling_phone", "whatsapp_phone"]);
migrate("leads_log", ["phone"]);
migrateAudit();
print("done");
