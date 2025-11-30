dependencies {
    implementation(project(":api"))
    implementation(libs.leyneck)
    compileOnly(libs.jetanno)
}

tasks.named("sourcesJar") {
    mustRunAfter(":api:jar")
}
