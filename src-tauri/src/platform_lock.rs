//! Native Windows session-lock and suspend notifications, independent of webview timers.
#[cfg(windows)]
pub fn install(callback: impl Fn() + Send + Sync + 'static) {
    use std::sync::OnceLock;
    use windows_sys::Win32::{
        Foundation::{HWND, LPARAM, LRESULT, WPARAM},
        System::{LibraryLoader::GetModuleHandleW, RemoteDesktop::WTSRegisterSessionNotification},
        UI::WindowsAndMessaging::*,
    };
    static CALLBACK: OnceLock<Box<dyn Fn() + Send + Sync>> = OnceLock::new();
    let _ = CALLBACK.set(Box::new(callback));
    unsafe extern "system" fn procedure(hwnd: HWND, message: u32, w: WPARAM, l: LPARAM) -> LRESULT {
        if (message == 0x02B1 && w == 7) || (message == WM_POWERBROADCAST && (w == 4 || w == 18)) {
            if let Some(cb) = CALLBACK.get() {
                cb();
            }
        }
        // SAFETY: Forward the window procedure arguments unchanged to the OS default handler.
        unsafe { DefWindowProcW(hwnd, message, w, l) }
    }
    std::thread::spawn(|| {
        let class: Vec<u16> = "RedMoonSecurityNotifications\0".encode_utf16().collect();
        // SAFETY: Class and title are valid terminated UTF-16; callbacks live for process lifetime.
        unsafe {
            let module = GetModuleHandleW(std::ptr::null());
            let wc = WNDCLASSW {
                lpfnWndProc: Some(procedure),
                hInstance: module,
                lpszClassName: class.as_ptr(),
                ..std::mem::zeroed()
            };
            if RegisterClassW(&wc) == 0 {
                return;
            }
            let window = CreateWindowExW(
                0,
                class.as_ptr(),
                class.as_ptr(),
                0,
                0,
                0,
                0,
                0,
                std::ptr::null_mut(),
                std::ptr::null_mut(),
                module,
                std::ptr::null(),
            );
            if window.is_null() {
                return;
            }
            WTSRegisterSessionNotification(window, 0);
            let mut message = std::mem::zeroed();
            while GetMessageW(&mut message, std::ptr::null_mut(), 0, 0) > 0 {
                TranslateMessage(&message);
                DispatchMessageW(&message);
            }
        }
    });
}
#[cfg(not(windows))]
pub fn install(_: impl Fn() + Send + Sync + 'static) {}
