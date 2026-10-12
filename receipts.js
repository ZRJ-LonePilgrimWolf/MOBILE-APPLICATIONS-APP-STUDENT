// Operation receipts: a retry with the same operation_id gets the SAME answer, and reusing an ID with DIFFERENT content is refused.
const crypto = require('crypto');

function hashOf(obj) {
    return crypto.createHash('sha256').update(JSON.stringify(obj)).digest('hex');
}

// What we store in operations.response_body: the answer, plus a fingerprint of the request.
function pack(body, hash) {
    return JSON.stringify({ _h: hash, body });
}

// Returns null if this operation_id is new, otherwise { status, body } to send back.
async function replay(conn, operationId, hash) {
    if (!operationId) return null;
    const [rows] = await conn.query(
        'SELECT http_status, response_body FROM operations WHERE operation_id = ?', [operationId]);
    if (rows.length === 0) return null;
    const stored = JSON.parse(rows[0].response_body);
    if (stored && stored._h !== undefined) {
        if (stored._h !== hash) {
            return { status: 422, body: { code: 'OPERATION_ID_REUSED', message: 'This operation ID was already used with different content' } };
        }
        return { status: rows[0].http_status, body: stored.body };
    }
    return { status: rows[0].http_status, body: stored };   // older receipts without a fingerprint
}

module.exports = { hashOf, pack, replay };
