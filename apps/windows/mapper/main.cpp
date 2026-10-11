#include <windows.h>
#include <shellapi.h>

#include <fstream>
#include <regex>
#include <string>
#include <vector>

namespace {
constexpr wchar_t WindowClassName[] = L"AppMapperMapperWindow";
constexpr int WindowWidth = 128;
constexpr int WindowHeight = 128;
constexpr BYTE DefaultOpacity = 218;

std::wstring Utf8ToWide(const std::string& value) {
    if (value.empty()) return L"";
    const int size = MultiByteToWideChar(CP_UTF8, 0, value.data(), static_cast<int>(value.size()), nullptr, 0);
    std::wstring result(size, L'\0');
    MultiByteToWideChar(CP_UTF8, 0, value.data(), static_cast<int>(value.size()), result.data(), size);
    return result;
}

std::wstring ExeDirectory() {
    std::vector<wchar_t> buffer(MAX_PATH);
    DWORD length = GetModuleFileNameW(nullptr, buffer.data(), static_cast<DWORD>(buffer.size()));
    while (length == buffer.size()) {
        buffer.resize(buffer.size() * 2);
        length = GetModuleFileNameW(nullptr, buffer.data(), static_cast<DWORD>(buffer.size()));
    }

    std::wstring path(buffer.data(), length);
    const auto slash = path.find_last_of(L"\\/");
    return slash == std::wstring::npos ? L"." : path.substr(0, slash);
}

std::wstring ExeStem() {
    std::vector<wchar_t> buffer(MAX_PATH);
    const DWORD length = GetModuleFileNameW(nullptr, buffer.data(), static_cast<DWORD>(buffer.size()));
    std::wstring path(buffer.data(), length);
    const auto slash = path.find_last_of(L"\\/");
    const auto dot = path.find_last_of(L'.');
    const auto start = slash == std::wstring::npos ? 0 : slash + 1;
    const auto end = dot == std::wstring::npos || dot < start ? path.size() : dot;
    return path.substr(start, end - start);
}

std::string ReadAllText(const std::wstring& path) {
    std::ifstream file(path, std::ios::binary);
    if (!file) return "";
    return std::string(std::istreambuf_iterator<char>(file), std::istreambuf_iterator<char>());
}

std::wstring ReadJsonString(const std::string& json, const std::string& key) {
    const std::regex pattern("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
    std::smatch match;
    if (!std::regex_search(json, match, pattern)) return L"";
    return Utf8ToWide(match[1].str());
}

BYTE ReadOpacity(const std::string& json) {
    const std::regex pattern("\"windowOpacity\"\\s*:\\s*(\\d+)");
    std::smatch match;
    if (!std::regex_search(json, match, pattern)) return DefaultOpacity;
    const int value = std::stoi(match[1].str());
    if (value < 40) return 40;
    if (value > 255) return 255;
    return static_cast<BYTE>(value);
}

HICON LoadMapperIcon(HINSTANCE instance, const std::wstring& directory) {
    HICON icon = LoadIconW(instance, MAKEINTRESOURCEW(1));
    if (icon != nullptr) return icon;

    const std::wstring icoPath = directory + L"\\icon.ico";
    return static_cast<HICON>(LoadImageW(
        nullptr,
        icoPath.c_str(),
        IMAGE_ICON,
        32,
        32,
        LR_LOADFROMFILE));
}

LRESULT CALLBACK WindowProc(HWND hwnd, UINT message, WPARAM wParam, LPARAM lParam) {
    switch (message) {
    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
    default:
        return DefWindowProcW(hwnd, message, wParam, lParam);
    }
}

void ShowAndFocusWindow(HWND hwnd, int x, int y, int width, int height) {
    const DWORD currentThread = GetCurrentThreadId();
    const HWND foregroundWindow = GetForegroundWindow();
    const DWORD foregroundThread = foregroundWindow != nullptr
        ? GetWindowThreadProcessId(foregroundWindow, nullptr)
        : 0;
    const bool attachedToForeground = foregroundThread != 0
        && foregroundThread != currentThread
        && AttachThreadInput(currentThread, foregroundThread, TRUE);

    ShowWindow(hwnd, SW_SHOWNORMAL);
    SetWindowPos(hwnd, HWND_TOPMOST, x, y, width, height, SWP_SHOWWINDOW);
    UpdateWindow(hwnd);
    BringWindowToTop(hwnd);
    SetActiveWindow(hwnd);
    SetFocus(hwnd);

    if (!SetForegroundWindow(hwnd)) {
        keybd_event(VK_MENU, 0, 0, 0);
        keybd_event(VK_MENU, 0, KEYEVENTF_KEYUP, 0);
        SetForegroundWindow(hwnd);
    }

    if (attachedToForeground) {
        AttachThreadInput(currentThread, foregroundThread, FALSE);
    }
}
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, PWSTR, int showCommand) {
    UNREFERENCED_PARAMETER(showCommand);

    const std::wstring directory = ExeDirectory();
    const std::string config = ReadAllText(directory + L"\\app.json");
    std::wstring title = ReadJsonString(config, "displayName");
    if (title.empty()) title = ExeStem();
    const BYTE opacity = ReadOpacity(config);

    HICON icon = LoadMapperIcon(instance, directory);

    WNDCLASSW wc{};
    wc.lpfnWndProc = WindowProc;
    wc.hInstance = instance;
    wc.lpszClassName = WindowClassName;
    wc.hIcon = icon;
    wc.hCursor = LoadCursorW(nullptr, IDC_ARROW);
    wc.hbrBackground = reinterpret_cast<HBRUSH>(COLOR_WINDOW + 1);
    RegisterClassW(&wc);

    const DWORD style = WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX;
    const DWORD exStyle = WS_EX_TOPMOST | WS_EX_LAYERED | WS_EX_APPWINDOW;
    RECT rect{0, 0, WindowWidth, WindowHeight};
    AdjustWindowRectEx(&rect, style, FALSE, exStyle);

    const int screenWidth = GetSystemMetrics(SM_CXSCREEN);
    const int screenHeight = GetSystemMetrics(SM_CYSCREEN);
    const int width = rect.right - rect.left;
    const int height = rect.bottom - rect.top;
    const int x = screenWidth - width - 24;
    const int y = screenHeight - height - 64;

    HWND hwnd = CreateWindowExW(
        exStyle,
        WindowClassName,
        title.c_str(),
        style,
        x,
        y,
        width,
        height,
        nullptr,
        nullptr,
        instance,
        nullptr);

    if (hwnd == nullptr) return 1;

    if (icon != nullptr) {
        SendMessageW(hwnd, WM_SETICON, ICON_SMALL, reinterpret_cast<LPARAM>(icon));
        SendMessageW(hwnd, WM_SETICON, ICON_BIG, reinterpret_cast<LPARAM>(icon));
    }

    SetLayeredWindowAttributes(hwnd, 0, opacity, LWA_ALPHA);
    ShowAndFocusWindow(hwnd, x, y, width, height);

    MSG message{};
    while (GetMessageW(&message, nullptr, 0, 0) > 0) {
        TranslateMessage(&message);
        DispatchMessageW(&message);
    }

    return 0;
}
