load("//third_party/java/arcs/build_defs:arcs_ts_test.bzl", "arcs_ts_test")
load(
    "//third_party/java/arcs/build_defs:build_defs.bzl",
    "arcs_cc_schema",
    "arcs_kt_gen",
    "arcs_kt_jvm_test_suite",
    "arcs_kt_schema",
    "arcs_proto_plan",
)

filegroup(
    name = "goldens",
    srcs = glob(["goldens/**"]),
)

filegroup(
    name = "generated",
    srcs = [
        ":golden.h",
        ":golden-kt_GeneratedSchemas.jvm.kt",
        ":golden-wasm_GeneratedSchemas.wasm.kt",
        ":golden_GeneratedSchemas.jvm.kt",
        ":golden_TestHarness.kt",
        "//java/arcs/core/data/testdata:WriterReaderExample_GeneratedPlan2",
    ],
)

arcs_cc_schema(
    name = "cc_schemas",
    src = "golden.arcs",
)

# golden_kt.arcs contains types that only work
# for Kotlin jvm. Don't try to build this using
# wasm support!
arcs_kt_schema(
    name = "kt_only_schemas",
    srcs = ["golden_kt.arcs"],
)

arcs_kt_schema(
    name = "kt_schemas",
    srcs = ["golden.arcs"],
    test_harness = True,
)

arcs_kt_schema(
    name = "kt_wasm_schemas",
    srcs = ["golden_wasm.arcs"],
    platforms = ["wasm"],
)

arcs_ts_test(
    name = "schema-generator-test",
    src = "schema-generator-test.ts",
    deps = [
        ":generated",
        ":goldens",
    ],
)

arcs_kt_gen(
    name = "variable_generation",
    srcs = ["Variable.arcs"],
    test_harness = False,
)

arcs_proto_plan(
    name = "variable_proto",
    src = "Variable.arcs",
)

arcs_kt_jvm_test_suite(
    name = "behavior-tests",
    srcs = glob(["*Test.kt"]),
    data = [":variable_proto.binarypb"],
    package = "src.tools.tests",
    deps = [
        ":variable_generation",
        "//java/arcs/core/common",
        "//java/arcs/core/crdt",
        "//java/arcs/core/data",
        "//java/arcs/core/data/proto:manifest_java_proto",
        "//java/arcs/core/data/proto:proto_for_test",
        "//java/arcs/core/data/util:data-util",
        "//java/arcs/core/entity",
        "//java/arcs/core/host",
        "//java/arcs/core/storage",
        "//java/arcs/core/storage/api",
        "//java/arcs/core/storage/driver",
        "//java/arcs/core/storage/driver/testutil",
        "//java/arcs/core/storage/keys",
        "//java/arcs/core/storage/referencemode",
        "//java/arcs/core/storage/testutil",
        "//java/arcs/core/testutil",
        "//java/arcs/core/util",
        "//java/arcs/core/util/testutil",
        "//java/arcs/jvm/host",
        "//java/arcs/jvm/util/testutil",
        "//java/arcs/repoutils",
        "//third_party/java/arcs/deps:protobuf_java",
        "//third_party/java/junit:junit-android",
        "//third_party/java/truth:truth-android",
        "//third_party/kotlin/kotlin:kotlin_test",
        "//third_party/kotlin/kotlinx_coroutines",
        "//third_party/kotlin/kotlinx_coroutines:kotlinx_coroutines_test",
    ],
)
