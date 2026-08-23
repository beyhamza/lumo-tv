// Stand-in for the `server-only` package under Vitest. The real package throws
// on import unless it is resolved through React's `react-server` condition,
// which is exactly the guarantee it exists to provide in the build — and
// exactly what makes it unimportable from a test runner.
export {};
