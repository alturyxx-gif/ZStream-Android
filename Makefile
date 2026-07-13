# ZStream Android — developer command menu.
#
# Requires GNU Make. On macOS it's preinstalled (or `brew install make`).
# On Windows, run this through Git Bash / MSYS2 (comes with `make` if you
# `choco install make`) or WSL — plain cmd.exe/PowerShell can't run Makefiles.
#
# Requires ANDROID_HOME (or ANDROID_SDK_ROOT) to be exported, e.g.:
#   macOS/Linux : export ANDROID_HOME=$HOME/Library/Android/sdk
#   Windows     : export ANDROID_HOME=$LOCALAPPDATA/Android/Sdk   (Git Bash)
#
# Usage examples:
#   make run                  # build, install and launch on the only connected device
#   make run-id ID=emulator-5554
#   make devices               # list connected devices/emulators
#   make logs ID=emulator-5554 # stream this app's logcat

.DEFAULT_GOAL := help
.PHONY: help setup submodules build debug release install install-release \
	run run-id run-release uninstall stop devices avds emulator logs logcat \
	screenshot clean clean-cache clear-app-data test plugin-dev plugin-release doctor

APP_ID        := com.zstream.android
MAIN_ACTIVITY := $(APP_ID)/.MainActivity
AVD           ?= Android_Device
ADB           := $(ANDROID_HOME)/platform-tools/adb
EMULATOR      := $(ANDROID_HOME)/emulator/emulator
DEVICE_FLAG   := $(if $(ID),-s $(ID),)

ifeq ($(OS),Windows_NT)
	GRADLEW := ./gradlew.bat
else
	GRADLEW := ./gradlew
endif

help: ## Show this list of commands
	@echo "ZStream Android — make targets"
	@echo ""
	@echo "  Setup"
	@echo "    make setup              Init git submodules + create local.properties"
	@echo "    make doctor             Print SDK/device/AVD diagnostic info"
	@echo ""
	@echo "  Build"
	@echo "    make build              Assemble debug APK (alias: make debug)"
	@echo "    make release            Assemble signed release APK"
	@echo "    make test               Run unit tests"
	@echo ""
	@echo "  Run"
	@echo "    make run                Install debug build + launch on the only connected device"
	@echo "    make run-id ID=<serial> Install debug build + launch on a specific device"
	@echo "    make run-release        Install + launch the release build"
	@echo "    make install            Build + install debug APK only (no launch)"
	@echo "    make install-release    Build + install release APK only (no launch)"
	@echo "    make uninstall [ID=]    Uninstall the app"
	@echo "    make stop [ID=]         Force-stop the app"
	@echo ""
	@echo "  Devices"
	@echo "    make devices            List connected devices/emulators"
	@echo "    make avds               List available emulator AVDs"
	@echo "    make emulator [AVD=]    Boot an emulator (default: $(AVD))"
	@echo "    make logs [ID=]         Stream logcat for this app (alias: make logcat)"
	@echo "    make screenshot [ID=]   Save a screenshot to ./screenshot.png"
	@echo ""
	@echo "  Cleanup"
	@echo "    make clean              gradle clean (removes build/ dirs)"
	@echo "    make clean-cache        Also wipe the Gradle cache (~/.gradle/caches) - fixes weird stale-build issues"
	@echo "    make clear-app-data [ID=]  Wipe the app's storage/cache ON THE DEVICE (adb pm clear)"
	@echo ""
	@echo "  Plugin (dev sideload — see README)"
	@echo "    make plugin-dev         Build debug plugin + push to all connected devices"
	@echo "    make plugin-release     Build release plugin + push to all connected devices"

setup: submodules ## Init submodules + local.properties
	@if [ ! -f local.properties ]; then \
		echo "sdk.dir=$(ANDROID_HOME)" > local.properties; \
		echo "Created local.properties -> sdk.dir=$(ANDROID_HOME)"; \
	else \
		echo "local.properties already exists, leaving it alone"; \
	fi

submodules: ## Init/update git submodules (libadb-android, zstream-plugin)
	git submodule update --init --recursive

doctor: ## Print environment diagnostics
	@echo "ANDROID_HOME = $(ANDROID_HOME)"
	@echo ""
	@echo "-- adb --"
	@$(ADB) version || echo "adb not found at $(ADB) — check ANDROID_HOME"
	@echo ""
	@echo "-- connected devices --"
	@$(ADB) devices -l || true
	@echo ""
	@echo "-- available AVDs --"
	@$(EMULATOR) -list-avds || echo "emulator not found at $(EMULATOR) — check ANDROID_HOME"

build debug: ## Assemble debug APK
	$(GRADLEW) assembleDebug

release: ## Assemble signed release APK
	$(GRADLEW) assembleRelease

test: ## Run unit tests
	$(GRADLEW) test

install: ## Build + install debug APK (no launch)
	$(GRADLEW) installDebug

install-release: ## Build + install release APK (no launch)
	$(GRADLEW) installRelease

run: install ## Build, install and launch the debug build
	$(ADB) $(DEVICE_FLAG) shell am start -n $(MAIN_ACTIVITY)

run-id: ## Build, install and launch on a specific device: make run-id ID=emulator-5554
	@if [ -z "$(ID)" ]; then echo "Usage: make run-id ID=<device-serial>  (see: make devices)"; exit 1; fi
	$(GRADLEW) installDebug
	$(ADB) -s $(ID) shell am start -n $(MAIN_ACTIVITY)

run-release: install-release ## Build, install and launch the release build
	$(ADB) $(DEVICE_FLAG) shell am start -n $(MAIN_ACTIVITY)

uninstall: ## Uninstall the app: make uninstall [ID=<serial>]
	$(ADB) $(DEVICE_FLAG) uninstall $(APP_ID)

stop: ## Force-stop the app: make stop [ID=<serial>]
	$(ADB) $(DEVICE_FLAG) shell am force-stop $(APP_ID)

devices: ## List connected devices/emulators
	$(ADB) devices -l

avds: ## List available emulator AVDs
	$(EMULATOR) -list-avds

emulator: ## Boot an emulator: make emulator [AVD=Android_Device]
	$(EMULATOR) -avd $(AVD) &

logs logcat: ## Stream this app's logcat: make logs [ID=<serial>]
	$(ADB) $(DEVICE_FLAG) logcat --pid=$$($(ADB) $(DEVICE_FLAG) shell pidof $(APP_ID))

screenshot: ## Save a screenshot to ./screenshot.png: make screenshot [ID=<serial>]
	$(ADB) $(DEVICE_FLAG) exec-out screencap -p > screenshot.png
	@echo "Saved screenshot.png"

clean: ## gradle clean (removes build/ output dirs)
	$(GRADLEW) clean

clean-cache: clean ## clean + wipe the Gradle cache (fixes stale/corrupt build weirdness)
	$(GRADLEW) --stop
	rm -rf ~/.gradle/caches
	rm -rf .gradle
	@echo "Gradle caches cleared. Next build will be slow (re-downloads dependencies)."

clear-app-data: ## Wipe the app's on-device storage/cache: make clear-app-data [ID=<serial>]
	$(ADB) $(DEVICE_FLAG) shell pm clear $(APP_ID)

plugin-dev: ## Build debug plugin APK + push to all connected devices
ifeq ($(OS),Windows_NT)
	powershell -ExecutionPolicy Bypass -File ./dev-run.ps1
else
	./dev-run.sh
endif

plugin-release: ## Build release plugin APK + push to all connected devices
ifeq ($(OS),Windows_NT)
	powershell -ExecutionPolicy Bypass -File ./release-run.ps1
else
	./release-run.sh
endif
