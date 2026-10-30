use red_moon_core::api::{Backend, Request};
use serde_json::{json, Value};
fn run(b: &mut Backend, v: Value) -> Value {
    b.execute(serde_json::from_value::<Request>(v).unwrap(), 1)
        .unwrap()
}
#[test]
fn flexible_records_conceal_secrets_and_category_delete_preserves_record() {
    let dir = tempfile::tempdir().unwrap();
    let mut b = Backend::open(dir.path().join("vault.rm")).unwrap();
    run(
        &mut b,
        json!({"action":"create","password":"synthetic master password"}),
    );
    run(
        &mut b,
        json!({"action":"save_category","category":{"id":"","name":"Personal","icon":"person","color":"red","order":0}}),
    );
    let categories = run(&mut b, json!({"action":"list_records"}))["categories"].clone();
    let category = categories.as_array().unwrap().last().unwrap()["id"]
        .as_str()
        .unwrap();
    run(
        &mut b,
        json!({"action":"save_record","record":{"id":"","kind":"identity","title":"Passport","category":category,"tags":[],"favorite":false,"created_at":0,"modified_at":0,"fields":[{"id":"number","label":"Number","field_type":"secret","value":"sensitive-number"}],"notes":"private notes","checklist":[{"id":"renew","text":"Renew","checked":false}]}}),
    );
    let list = run(&mut b, json!({"action":"list_records"}));
    assert!(!list.to_string().contains("sensitive-number"));
    assert!(!list.to_string().contains("private notes"));
    let id = list["records"][0]["id"].as_str().unwrap();
    let detail = run(&mut b, json!({"action":"record_detail","id":id}));
    assert_eq!(detail["record"]["fields"][0]["value"], "");
    assert_eq!(detail["record"]["fields"][0]["concealed"], true);
    assert_eq!(
        run(
            &mut b,
            json!({"action":"reveal_field","id":id,"field_id":"number"})
        )["value"],
        "sensitive-number"
    );
    assert_eq!(
        run(&mut b, json!({"action":"edit_record","id":id}))["record"]["fields"][0]["value"],
        "sensitive-number"
    );
    run(&mut b, json!({"action":"delete_category","id":category}));
    let list = run(&mut b, json!({"action":"list_records"}));
    assert_eq!(list["records"].as_array().unwrap().len(), 1);
    assert_eq!(list["records"][0]["category"], "");
}
#[test]
fn writes_version_two_and_biometric_key_authenticates_current_file() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("vault.rm");
    let mut b = Backend::open(path.clone()).unwrap();
    run(
        &mut b,
        json!({"action":"create","password":"synthetic master password"}),
    );
    assert_eq!(std::fs::read(&path).unwrap()[8], 2);
    let password = secrecy::SecretString::new("synthetic master password".into());
    let key = b.vault.biometric_key(&password).unwrap();
    b.vault.lock();
    b.vault.unlock_with_key(&key).unwrap();
    b.vault
        .change_password(
            &password,
            &secrecy::SecretString::new("another synthetic password".into()),
        )
        .unwrap();
    b.vault.lock();
    assert!(b.vault.unlock_with_key(&key).is_err());
}
#[test]
fn category_order_is_a_permutation_and_vault_deletion_requires_reauthentication() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("vault.rm");
    let mut b = Backend::open(path.clone()).unwrap();
    run(
        &mut b,
        json!({"action":"create","password":"synthetic master password"}),
    );
    let list = run(&mut b, json!({"action":"list_records"}));
    let mut ids: Vec<String> = list["categories"]
        .as_array()
        .unwrap()
        .iter()
        .map(|c| c["id"].as_str().unwrap().to_string())
        .collect();
    ids.reverse();
    run(&mut b, json!({"action":"reorder_categories","ids":ids}));
    let list = run(&mut b, json!({"action":"list_records"}));
    assert_eq!(list["categories"][0]["id"], ids[0]);
    assert!(b
        .execute(
            serde_json::from_value(
                json!({"action":"reorder_categories","ids":[ids[0],ids[0],ids[0]]})
            )
            .unwrap(),
            1
        )
        .is_err());
    assert!(b
        .execute(
            serde_json::from_value(json!({"action":"delete_vault","password":"wrong"})).unwrap(),
            1
        )
        .is_err());
    assert!(path.exists());
    run(
        &mut b,
        json!({"action":"delete_vault","password":"synthetic master password"}),
    );
    assert!(!path.exists());
    assert!(b.execute(Request::ListRecords, 1).is_err());
}
#[test]
fn legacy_named_category_follows_rename_and_delete_without_losing_records() {
    let dir = tempfile::tempdir().unwrap();
    let mut b = Backend::open(dir.path().join("vault.rm")).unwrap();
    run(
        &mut b,
        json!({"action":"create","password":"synthetic master password"}),
    );
    run(
        &mut b,
        json!({"action":"save","entry":{"id":"","title":"Legacy account","username":"synthetic","password":"synthetic","url":"","category":"Personal","tags":[],"notes":"","favorite":false,"created_at":0,"modified_at":0}}),
    );
    run(
        &mut b,
        json!({"action":"save_category","category":{"id":"personal","name":"Private","icon":"person","color":"#9333ea","order":0}}),
    );
    assert_eq!(
        run(&mut b, json!({"action":"list_records"}))["records"][0]["category"],
        "personal"
    );
    run(&mut b, json!({"action":"delete_category","id":"personal"}));
    let list = run(&mut b, json!({"action":"list_records"}));
    assert_eq!(list["records"].as_array().unwrap().len(), 1);
    assert_eq!(list["records"][0]["category"], "");
}
#[test]
fn default_categories_cover_personal_vault_workflows() {
    let names: Vec<String> = red_moon_core::model::default_categories()
        .iter()
        .map(|c| c.name.clone())
        .collect();
    for name in [
        "Email accounts",
        "Social media",
        "Banking",
        "Finance",
        "Government documents",
        "Work",
        "Personal",
        "Shopping",
        "Education",
        "Other",
    ] {
        assert!(names.iter().any(|n| n == name), "Missing category {name}");
    }
}
#[test]
fn note_body_search_returns_only_summaries() {
    let dir = tempfile::tempdir().unwrap();
    let mut b = Backend::open(dir.path().join("vault.rm")).unwrap();
    run(
        &mut b,
        json!({"action":"create","password":"synthetic master password"}),
    );
    run(
        &mut b,
        json!({"action":"save_record","record":{"id":"","kind":"secure_note","title":"Instructions","category":"","tags":[],"favorite":false,"created_at":0,"modified_at":0,"fields":[],"notes":"synthetic recovery procedure only","checklist":[]}}),
    );
    let request = serde_json::from_value::<Request>(
        json!({"action":"search_records","query":"recovery procedure"}),
    );
    assert!(
        request.is_ok(),
        "backend must support private note-body searches"
    );
    let found = b.execute(request.unwrap(), 1).unwrap();
    assert_eq!(found["records"].as_array().unwrap().len(), 1);
    assert!(!found
        .to_string()
        .contains("synthetic recovery procedure only"));
    let found = b
        .execute(
            serde_json::from_value(json!({"action":"search_records","query":"absent"})).unwrap(),
            1,
        )
        .unwrap();
    assert!(found["records"].as_array().unwrap().is_empty());
}
#[test]
fn every_record_kind_survives_edit_reopen_backup_restore_and_deletion() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("vault.rm");
    let mut b = Backend::open(path.clone()).unwrap();
    run(
        &mut b,
        json!({"action":"create","password":"synthetic master password"}),
    );
    for kind in [
        "login",
        "secure_note",
        "identity",
        "indian_document",
        "financial",
        "custom",
    ] {
        run(
            &mut b,
            json!({"action":"save_record","record":{"id":"","kind":kind,"title":format!("synthetic {kind}"),"category":"personal","tags":["test"],"favorite":true,"created_at":0,"modified_at":0,"fields":[{"id":"private","label":"Synthetic private value","field_type":"secret","value":"synthetic-only"},{"id":"date","label":"Date","field_type":"date","value":"2000-01-01"}],"notes":"synthetic multiline\nsecond line","checklist":[{"id":"check","text":"Synthetic task","checked":false}]}}),
        );
    }
    let list = run(&mut b, json!({"action":"list_records"}));
    let ids: Vec<String> = list["records"]
        .as_array()
        .unwrap()
        .iter()
        .map(|r| r["id"].as_str().unwrap().to_owned())
        .collect();
    for id in &ids {
        let mut record = run(&mut b, json!({"action":"edit_record","id":id}))["record"].clone();
        record["title"] = json!(format!("edited {}", record["kind"].as_str().unwrap()));
        record["fields"][0]["value"] = json!("edited synthetic secret");
        record["checklist"][0]["checked"] = json!(true);
        run(&mut b, json!({"action":"save_record","record":record}));
    }
    drop(b);
    let mut b = Backend::open(path).unwrap();
    run(
        &mut b,
        json!({"action":"unlock","password":"synthetic master password"}),
    );
    for id in &ids {
        let record = run(&mut b, json!({"action":"edit_record","id":id}));
        assert_eq!(
            record["record"]["fields"][0]["value"],
            "edited synthetic secret"
        );
        assert_eq!(
            record["record"]["notes"],
            "synthetic multiline\nsecond line"
        );
        assert_eq!(record["record"]["checklist"][0]["checked"], true);
    }
    let backup = dir.path().join("copy.rm");
    b.vault.backup(&backup).unwrap();
    let mut fresh = Backend::open(dir.path().join("fresh.rm")).unwrap();
    fresh
        .vault
        .restore(
            &backup,
            &secrecy::SecretString::new("synthetic master password".into()),
            None,
        )
        .unwrap();
    fresh.start(1).unwrap();
    assert_eq!(
        run(&mut fresh, json!({"action":"list_records"}))["records"]
            .as_array()
            .unwrap()
            .len(),
        6
    );
    for id in &ids {
        run(&mut fresh, json!({"action":"delete_record","id":id}));
    }
    assert!(run(&mut fresh, json!({"action":"list_records"}))["records"]
        .as_array()
        .unwrap()
        .is_empty());
}
