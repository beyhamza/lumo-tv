// Root build file. It declares plugin versions and nothing else.
//
// There is deliberately no `subprojects { }` or `allprojects { }` block here:
// shared configuration lives in build-logic/ convention plugins, which a module
// opts into by id. Cross-cutting configuration applied from the root is
// invisible from the module that receives it and defeats the configuration
// cache.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.openapi.generator) apply false
}
