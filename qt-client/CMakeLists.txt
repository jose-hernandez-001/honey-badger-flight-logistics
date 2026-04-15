cmake_minimum_required(VERSION 3.20)

project(FlightLogisticsClient
    VERSION     1.0.0
    DESCRIPTION "Honey Badger Flight Logistics Qt desktop client"
    LANGUAGES   CXX)

set(CMAKE_CXX_STANDARD          17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC               ON)

# ── Qt ───────────────────────────────────────────────────────────────────────
find_package(Qt6 REQUIRED COMPONENTS Core Widgets Network)

# ── Protobuf ─────────────────────────────────────────────────────────────────
find_package(Protobuf REQUIRED)

# ── gRPC plugin ──────────────────────────────────────────────────────────────
find_program(GRPC_CPP_PLUGIN grpc_cpp_plugin REQUIRED
    HINTS /usr/bin /usr/local/bin
    DOC   "gRPC C++ protoc plugin (install with: apt install protobuf-compiler-grpc)")

# ── gRPC library ─────────────────────────────────────────────────────────────
find_package(gRPC CONFIG QUIET)
if(TARGET gRPC::grpc++)
    set(GRPC_LINK_TARGET gRPC::grpc++)
else()
    find_package(PkgConfig REQUIRED)
    pkg_check_modules(GRPCPP REQUIRED IMPORTED_TARGET grpc++)
    set(GRPC_LINK_TARGET PkgConfig::GRPCPP)
endif()

# ── Proto code generation ────────────────────────────────────────────────────
set(PROTO_SRC_DIR "${CMAKE_CURRENT_SOURCE_DIR}/proto")
set(PROTO_OUT_DIR "${CMAKE_CURRENT_BINARY_DIR}/proto-gen")
file(MAKE_DIRECTORY "${PROTO_OUT_DIR}/models")
file(MAKE_DIRECTORY "${PROTO_OUT_DIR}/services")

# compile_proto(<absolute-proto-path> <use-grpc> <srcs-var> <hdrs-var>)
#
# Schedules a custom command that runs protoc and appends the generated .cc
# and .h paths to <srcs-var> and <hdrs-var> in the caller's scope.
function(compile_proto PROTO_FILE USE_GRPC OUT_SRCS OUT_HDRS)
    cmake_path(RELATIVE_PATH PROTO_FILE
        BASE_DIRECTORY "${PROTO_SRC_DIR}"
        OUTPUT_VARIABLE PROTO_REL)
    cmake_path(GET PROTO_REL STEM      LAST_ONLY PROTO_STEM)
    cmake_path(GET PROTO_REL PARENT_PATH          PROTO_SUBDIR)

    set(OUT_SUBDIR "${PROTO_OUT_DIR}/${PROTO_SUBDIR}")

    set(PB_CC "${OUT_SUBDIR}/${PROTO_STEM}.pb.cc")
    set(PB_H  "${OUT_SUBDIR}/${PROTO_STEM}.pb.h")

    if(USE_GRPC)
        set(GRPC_CC "${OUT_SUBDIR}/${PROTO_STEM}.grpc.pb.cc")
        set(GRPC_H  "${OUT_SUBDIR}/${PROTO_STEM}.grpc.pb.h")
        set(GRPC_CMD_ARGS
            "--grpc_out=${PROTO_OUT_DIR}"
            "--plugin=protoc-gen-grpc=${GRPC_CPP_PLUGIN}"
        )
        set(GRPC_OUT_FILES "${GRPC_CC}" "${GRPC_H}")
    else()
        set(GRPC_CMD_ARGS    "")
        set(GRPC_OUT_FILES   "")
    endif()

    add_custom_command(
        OUTPUT  "${PB_CC}" "${PB_H}" ${GRPC_OUT_FILES}
        COMMAND "${CMAKE_COMMAND}" -E make_directory "${OUT_SUBDIR}"
        COMMAND "${Protobuf_PROTOC_EXECUTABLE}"
                "--cpp_out=${PROTO_OUT_DIR}"
                ${GRPC_CMD_ARGS}
                "-I${PROTO_SRC_DIR}"
                "-I${Protobuf_INCLUDE_DIRS}"
                "${PROTO_FILE}"
        DEPENDS "${PROTO_FILE}"
        COMMENT "Compiling proto ${PROTO_REL}"
        VERBATIM
    )

    set(${OUT_SRCS} ${${OUT_SRCS}} "${PB_CC}" ${GRPC_OUT_FILES} PARENT_SCOPE)
    set(${OUT_HDRS} ${${OUT_HDRS}} "${PB_H}"  ${GRPC_OUT_FILES} PARENT_SCOPE)
endfunction()

set(PROTO_SRCS "")
set(PROTO_HDRS "")

# Models – protobuf only, no gRPC
foreach(PROTO IN ITEMS
        proto/models/page_metadata.proto
        proto/models/problem_detail.proto
        proto/models/route_status.proto
        proto/models/route.proto
        proto/models/route_page.proto
        proto/models/route_patch.proto
        proto/models/route_request.proto
        proto/models/waypoint.proto
        proto/models/waypoint_list.proto
        proto/models/waypoint_page.proto
        proto/models/waypoint_patch.proto
        proto/models/waypoint_reorder_request.proto
        proto/models/waypoint_request.proto)
    cmake_path(ABSOLUTE_PATH PROTO OUTPUT_VARIABLE ABS_PROTO)
    compile_proto("${ABS_PROTO}" FALSE PROTO_SRCS PROTO_HDRS)
endforeach()

# Services – protobuf + gRPC stubs
foreach(PROTO IN ITEMS
        proto/services/routes_service.proto
        proto/services/waypoints_service.proto)
    cmake_path(ABSOLUTE_PATH PROTO OUTPUT_VARIABLE ABS_PROTO)
    compile_proto("${ABS_PROTO}" TRUE PROTO_SRCS PROTO_HDRS)
endforeach()

# ── Application target ───────────────────────────────────────────────────────
qt_add_executable(flight-logistics-client
    src/main.cpp
    src/mainwindow.cpp
    src/grpcclient.cpp
    src/mapwidget.cpp
    ${PROTO_SRCS}
)

qt_add_resources(flight-logistics-client "app_resources"
    PREFIX "/"
    FILES
        resources/airplane.png
)

target_include_directories(flight-logistics-client PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/src"
    "${PROTO_OUT_DIR}"
    "${Protobuf_INCLUDE_DIRS}"
)

target_link_libraries(flight-logistics-client PRIVATE
    Qt6::Core
    Qt6::Widgets
    Qt6::Network
    protobuf::libprotobuf
    ${GRPC_LINK_TARGET}
)
