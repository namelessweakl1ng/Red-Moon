`legacy-v1.rm` is an authenticated, synthetic v1 vault. It contains no real credentials.

Password: `synthetic legacy fixture password`.

The independent writer in `../make_fixture.rs` spells out the original v1 JSON schema and authenticated header, derives its key with Argon2id (65536 KiB, 3 iterations, 1 lane), and encrypts using XChaCha20-Poly1305 with the complete 80-byte header as associated data. Salt and nonce are fixed solely to make this test fixture reproducible. Production encryption always generates a random nonce.

Regenerate explicitly with `cargo test -p red-moon-core --test make_fixture -- --ignored`.
