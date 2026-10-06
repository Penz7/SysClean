package vn.sysclean.core.privilege.shell

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ShellModule {
    @Binds
    abstract fun bindsPrivilegedShell(impl: CompositePrivilegedShell): PrivilegedShell

    @Binds
    abstract fun bindsProcessRunner(impl: SystemProcessRunner): ProcessRunner
}
