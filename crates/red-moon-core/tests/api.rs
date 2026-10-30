use red_moon_core::{
    api::{Backend, Request},
    model::Entry,
};
use secrecy::SecretString;
#[test]
fn commands_guard_secrets_summarize_and_expire() {
    let dir = tempfile::tempdir().unwrap();
    let mut b = Backend::open(dir.path().join("vault.rm")).unwrap();
    assert!(b.execute(Request::List, 0).is_err());
    b.execute(
        Request::Create {
            password: SecretString::new("very long master passphrase".into()),
        },
        0,
    )
    .unwrap();
    b.execute(
        Request::Save {
            entry: Entry {
                id: String::new(),
                title: "Example".into(),
                username: "alice".into(),
                password: "never in summaries".into(),
                url: String::new(),
                notes: "secret note".into(),
                category: String::new(),
                tags: vec![],
                favorite: false,
                created_at: 0,
                modified_at: 0,
            },
        },
        1,
    )
    .unwrap();
    let list = b.execute(Request::List, 2).unwrap();
    let text = serde_json::to_string(&list).unwrap();
    assert!(!text.contains("never in summaries"));
    assert!(!text.contains("secret note"));
    let id = list["entries"][0]["id"].as_str().unwrap().to_string();
    assert!(b.execute(Request::Reveal { id: id.clone() }, 300).is_err());
    assert!(b.execute(Request::Detail { id }, 301).is_err());
}
