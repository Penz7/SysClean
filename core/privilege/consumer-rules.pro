# Shizuku instantiates the privileged service by class name in a separate shell process.
-keep class vn.sysclean.core.privilege.shell.PrivilegedService { <init>(...); *; }
-keep class vn.sysclean.core.privilege.IPrivilegedService { *; }
-keep class vn.sysclean.core.privilege.IPrivilegedService$* { *; }
