package io.github.sammeeeeeeee.onenet.shell;

import android.os.Bundle;

interface IShellService {
    // Transaction code reserved by Shizuku for stopping the service.
    void destroy() = 16777114;

    // Runs `sh -c command`. Result keys: "code" (int), "out" and "err" (String).
    Bundle exec(String command) = 1;
}
