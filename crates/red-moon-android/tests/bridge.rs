use red_moon_android::Bridge;
#[test]
fn authentication_and_expiry_through_mobile_boundary() {
    let d = tempfile::tempdir().unwrap();
    let mut b = Bridge::open(d.path().join("vault.rm")).unwrap();
    assert!(b.request(r#"{"action":"list"}"#, 0)["error"].is_string());
    assert!(b.request(
        r#"{"action":"create","password":"long mobile master passphrase"}"#,
        0
    )["ok"]
        .is_object());
    assert!(b.request(r#"{"action":"list"}"#, 1)["ok"]["entries"].is_array());
    assert!(b.request(r#"{"action":"list"}"#, 300)["error"].is_string());
    assert!(b.request("malformed", 301)["error"].is_string());
}
#[test]
fn encrypted_mobile_backup_rejects_wrong_password_and_restores() {
    let d = tempfile::tempdir().unwrap();
    let path = d.path().join("vault.rm");
    let mut b = Bridge::open(path.clone()).unwrap();
    b.request(
        r#"{"action":"create","password":"long mobile master passphrase"}"#,
        0,
    );
    let saved = b.request(r#"{"action":"backup"}"#, 1);
    let data = saved["ok"]["data"].as_str().unwrap();
    let before = std::fs::read(&path).unwrap();
    let bad = serde_json::json!({"action":"restore","data":data,"password":"incorrect","current":"long mobile master passphrase"});
    assert!(b.request(&bad.to_string(), 2)["error"].is_string());
    assert_eq!(before, std::fs::read(&path).unwrap());
    let good = serde_json::json!({"action":"restore","data":data,"password":"long mobile master passphrase","current":"long mobile master passphrase"});
    assert!(b.request(&good.to_string(), 3).get("error").is_none());
    assert!(b.request(r#"{"action":"list"}"#, 4)["ok"]["entries"].is_array());
}
