.PHONY: build
build:
	../gradlew clean build

.PHONY: install
install:
	rm -r tmp || true
	mkdir tmp
	cd tmp && tar -xvf ../build/distributions/ecco-cli-0.1.9.tar -C ./
	cd tmp/ecco-cli-0.1.9 && mv ./bin/* /usr/local/bin/ && mv ./lib/* /usr/local/lib/
	rm -r tmp
