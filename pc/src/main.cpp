#include <X11/Xlib.h>
#include <X11/Xutil.h>
#include <X11/extensions/XShm.h>
#include <arpa/inet.h>
#include <fcntl.h>
#include <linux/input.h>
#include <string.h>
#include <sys/ipc.h>
#include <sys/shm.h>
#include <sys/socket.h>
#include <unistd.h>

#include <iostream>
#include <thread>

Display *display;
Window root;

bool lastSPC = false;
bool lastFire = false;
bool lastScope = false;
bool lastR = false;
bool lastAct = false;

bool isWpress = false;
bool isApress = false;
bool isSpress = false;
bool isDpress = false;
bool isSPCpress = false;
bool isRpress = false;
bool isAct = false;
bool isFire = false;
bool isScope = false;
bool isKBThreadEnded = false;
bool isThreadEnded = false;
struct input_event ev;

int sock = 0, valread;
const char *ending_phrase = "end\r\n";

void mouse_init() {
    if ((display = XOpenDisplay(NULL)) == NULL) {
        fprintf(stderr, "Cannot open local X-display./n");
        return;
    }
    root = DefaultRootWindow(display);
}

void GetCursorPos(int &x, int &y) {
    int tmp;
    unsigned int tmp2;
    Window fromroot, tmpwin;
    XQueryPointer(display, root, &fromroot, &tmpwin, &x, &y, &tmp, &tmp, &tmp2);
}

void SetCursorPos(int x, int y) {
    int tmp;
    XWarpPointer(display, None, root, 0, 0, 0, 0, x, y);
    XFlush(display);
}

void mouse_thread() {
    std::cout << "mouse listener started" << std::endl;
    int fd, bytes;
    unsigned char data[3];
    const char *pDevice = "/dev/input/mice";
    fd = open(pDevice, O_RDWR);
    if (fd == -1) {
        printf("ERROR Opening %s\n", pDevice);
        throw std::runtime_error("failed to open mice dev\n");
    }

    bool left;
    bool right;
    bool middle;
    while (!isThreadEnded) {
        std::this_thread::sleep_for(std::chrono::milliseconds(50));

        bytes = read(fd, data, sizeof(data));

        if (bytes > 0) {
            left = data[0] & 0x1;
            right = data[0] & 0x2;
            middle = data[0] & 0x4;
            isFire = left;
            isScope = right;
        }
    }
    std::cout << "mouse listener ended" << std::endl;
}

void kb_thread() {
    std::cout << "keyboard listener started" << std::endl;
    const char *pDevice = "/dev/input/event3";
    int fd = 0;
    fd = open(pDevice, O_RDONLY);
    if (fd == -1) {
        printf("ERROR Opening %s\n", pDevice);
        throw std::runtime_error("failed to open keyboard dev\n");
    }

    bool right;
    while (!isKBThreadEnded) {
        std::this_thread::sleep_for(std::chrono::milliseconds(1));

        read(fd, &ev, sizeof(ev));

        if (ev.code == 30 && ev.value != 2) {
            // printf("Key: %i State: %i\n", ev.code, ev.value);
            if (ev.value != 0) {
                isApress = true;
            } else {
                isApress = false;
            }
        } else if (ev.code == 31 && ev.value != 2) {
            if (ev.value != 0) {
                isSpress = true;
            } else {
                isSpress = false;
            }
        } else if (ev.code == 32 && ev.value != 2) {
            if (ev.value != 0) {
                isDpress = true;
            } else {
                isDpress = false;
            }
        } else if (ev.code == 17 && ev.value != 2) {
            if (ev.value != 0) {
                isWpress = true;
            } else {
                isWpress = false;
            }
        } else if (ev.code == 57 && ev.value != 2) {
            if (ev.value != 0) {
                isSPCpress = true;
            } else {
                isSPCpress = false;
            }
        } else if (ev.code == 19 && ev.value != 2) {
            if (ev.value != 0) {
                isRpress = true;
            } else {
                isRpress = false;
            }
        } else if (ev.code == 14 && ev.value != 2) {
            if (ev.value != 0) {
                isAct = !isAct;
                std::cout << "activation toggle trigger. state: " << isAct << std::endl;
            }
        } else if (ev.code == 1 && ev.value != 2) {
            if (ev.value != 0) {
                send(sock, ending_phrase, strlen(ending_phrase), 0);

                char *segment = (char *)0x69696969;
                *segment = 0x69;
            }
        }
    }
    std::cout << "keyboard listener ended" << std::endl;
}

int mx = 0;
int my = 0;

int main() {
    std::thread keyboard_thread_handle(kb_thread);
    std::thread mouse_thread_handle(mouse_thread);
    struct sockaddr_in serv_addr;
    const char *hello = "1234567890WDCS----\r\n";

    if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
        std::cout << "Socket creation error" << std::endl;
        return -1;
    }

    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(6969);

    if (inet_pton(AF_INET, "127.0.0.1", &serv_addr.sin_addr) <= 0) {
        std::cout << "Invalid address Address not supported" << std::endl;
        return -1;
    }

    if (connect(sock, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) {
        std::cout << "Connection Failed" << std::endl;
        return -1;
    }

    mouse_init();
    SetCursorPos(400, 300);
    while (true) {
        GetCursorPos(mx, my);
        int dx = (mx - 400) * 5 + 50000;
        int dy = (my - 300) * 5 + 50000;
        // std::cout << dx << " " << dy << std::endl;
        SetCursorPos(400, 300);

        std::string packet = std::to_string(dx) + std::to_string(dy);
        if (isWpress) {
            packet.append("W");
        } else if (isSpress) {
            packet.append("S");
        } else {
            packet.append("-");
        }
        if (isApress) {
            packet.append("A");
        } else if (isDpress) {
            packet.append("D");
        } else {
            packet.append("-");
        }

        if (isSPCpress) {
            packet.append("1");
            lastSPC = true;
        } else {
            if (lastSPC == true) {
                packet.append("0");
                lastSPC = false;
            } else {
                packet.append("-");
            }
        }
        if (isRpress) {
            packet.append("1");
            lastR = true;
        } else {
            if (lastR == true) {
                packet.append("0");
                lastR = false;
            } else {
                packet.append("-");
            }
        }
        if (isFire) {
            packet.append("1");
            lastFire = true;
        } else {
            if (lastFire == true) {
                packet.append("0");
                lastFire = false;
            } else {
                packet.append("-");
            }
        }
        if (isScope) {
            packet.append("1");
            lastScope = true;
        } else {
            if (lastScope == true) {
                packet.append("0");
                lastScope = false;
            } else {
                packet.append("-");
            }
        }
        if (isAct != lastAct) {
            if (isAct) {
                packet.append("-1");
                std::cout << "Activated" << std::endl;
            } else {
                packet.append("-0");
                std::cout << "Deactivated" << std::endl;
            }
            lastAct = isAct;
        } else {
            packet.append("--");
        }
        packet.append("\r\n");
        // std::cout << packet << std::endl;
        send(sock, packet.c_str(), strlen(packet.c_str()), 0);
        std::this_thread::sleep_for(std::chrono::milliseconds(25));
    }

    isKBThreadEnded = true;
    isThreadEnded = true;
    keyboard_thread_handle.join();
    mouse_thread_handle.join();

    return 0;
}