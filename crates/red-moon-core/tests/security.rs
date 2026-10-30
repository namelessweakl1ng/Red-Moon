use red_moon_core::{
    generator::{generate, Options},
    session::{ClipboardLease, Session},
};
use secrecy::ExposeSecret;
#[test]
fn generation_validates_and_covers_selected_classes() {
    for length in [12, 24, 128] {
        let p = generate(&Options {
            length,
            lowercase: true,
            uppercase: true,
            digits: true,
            symbols: true,
        })
        .unwrap();
        let s = p.expose_secret();
        assert_eq!(s.len(), length);
        assert!(s.chars().any(|c| c.is_ascii_lowercase()));
        assert!(s.chars().any(|c| c.is_ascii_uppercase()));
        assert!(s.chars().any(|c| c.is_ascii_digit()));
        assert!(s.chars().any(|c| "!@#$%^&*-_=+?".contains(c)));
    }
    assert!(generate(&Options {
        length: 11,
        ..Options::default()
    })
    .is_err());
    assert!(generate(&Options {
        length: 129,
        ..Options::default()
    })
    .is_err());
    assert!(generate(&Options {
        length: 20,
        lowercase: false,
        uppercase: false,
        digits: false,
        symbols: false
    })
    .is_err());
}
#[test]
fn session_expires_and_rejects_stale_generation() {
    let mut s = Session::default();
    s.start(100, 300);
    let generation = s.generation;
    assert!(s.active(399));
    assert!(!s.active(400));
    assert_ne!(s.generation, generation);
    s.start(500, 300);
    s.touch(600);
    assert!(s.active(899));
    assert!(!s.active(900));
    s.start(1000, 300);
    s.lock();
    assert!(!s.active(1000));
}
#[test]
fn clipboard_only_clears_owned_content_after_twenty_seconds() {
    let l = ClipboardLease::new("password".into(), 100, 3);
    assert!(!l.should_clear("password", 119, 3));
    assert!(l.should_clear("password", 120, 3));
    assert!(!l.should_clear("newer text", 120, 3));
    assert!(!l.should_clear("password", 120, 4));
}
