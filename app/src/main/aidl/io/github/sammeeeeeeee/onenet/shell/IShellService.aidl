package io.github.sammeeeeeeee.onenet.shell;

import android.os.Bundle;

interface IShellService {
    // Transaction code reserved by Shizuku for stopping the service.
    void destroy() = 16777114;

    // Runs `sh -c command`. Result keys: "code" (int), "out" and "err" (String).
    Bundle exec(String command) = 1;

    // Wi-Fi service calls that `cmd wifi` reserves for root. False when the call fails.
    // Pause: drop Wi-Fi and auto-join only the given SIM's carrier networks, as Pixel does.
    boolean pauseWifi(int subId) = 2;
    boolean resumeWifi() = 3;
    boolean connectSavedNetwork(int networkId) = 4;
}
