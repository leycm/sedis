dependencies {
    compileOnly(libs.leyneck)
    compileOnly(libs.jetanno)

    compileOnly(libs.gson)
    compileOnly(libs.slf4j)
    compileOnly(libs.logback)

    compileOnly("redis.clients:jedis:5.1.0")

    // test dependencies
    testImplementation(libs.leyneck)

    testImplementation(libs.gson)
    testImplementation(libs.slf4j)
    testImplementation(libs.logback)

    testImplementation("redis.clients:jedis:5.1.0")
}