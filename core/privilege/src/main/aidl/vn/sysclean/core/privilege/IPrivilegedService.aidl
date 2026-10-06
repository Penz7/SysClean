package vn.sysclean.core.privilege;

import android.os.Bundle;

// Runs inside a Shizuku "user service": a separate process with the shell (adb) identity.
interface IPrivilegedService {
    // Shizuku calls this transaction to stop the service; the code is fixed by Shizuku.
    void destroy() = 16777114;

    // Keys: "exit" (int), "out" (String), "err" (String), "timedOut" (boolean).
    Bundle execute(in String[] command, long timeoutMillis) = 1;

    String[] listDirectory(String path) = 2;

    long sizeOf(String path) = 3;

    // Deletes everything inside [path], keeping the folder itself. Returns bytes freed.
    long deleteContents(String path) = 4;

    boolean deleteRecursively(String path) = 5;
}
