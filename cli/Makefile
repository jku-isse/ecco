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

.PHONY: create-test-environment
create-test-environment:
ifeq (, $(shell which ids))
	$(error "No 'ids' in $(PATH), build and install image-upload project for DesignSpace")
endif
ifeq (, $(shell which ds))
	$(error "No 'ds' in $(PATH), build and install cli project for DesignSpace")
endif
	ds user register michael
	ds tool register cli 0.0.1

	ds workspace create "person,purple_shirt" 2 1 # assume this is going to be workspace 2
	ds workspace create "person,striped_shirt" 2 1
	ds workspace create "person,purple_shirt,jacket" 2 1
	ds workspace create "person,purple_shirt,jacket,glasses" 2 1
#	ds workspace create "person,striped_shirt,jacket,glasses" 2 1
#	ds workspace create "person,striped_shirt,glasses" 2 1
#	ds workspace create "person,purple_shirt,glasses" 2 1
#	ds workspace create "person,striped_shirt,jacket" 2 1
#	ds workspace create "person,striped_shirt,jacket,hat" 2 1

	ds workspace

	cd ../examples/image_variants/V1_purpleshirt/ && ids image upload 2 "person.png"
	cd ../examples/image_variants/V2_stripedshirt/ && ids image upload 3 "person.png"
	cd ../examples/image_variants/V3_purpleshirt_jacket/ && ids image upload 4 "person.png"
	cd ../examples/image_variants/V4_purpleshirt_jacket_glasses/ && ids image upload 5 "person.png"
#	cd ../examples/image_variants/V5_stripedshirt_jacket_glasses/ && ids image upload 6 "person.png"
#	cd ../examples/image_variants/V6_stripedshirt_glasses/ && ids image upload 7 "person.png"
#	cd ../examples/image_variants/V7_purpleshirt_glasses/ && ids image upload 8 "person.png"
#	cd ../examples/image_variants/V8_stripedshirt_jacket/ && ids image upload 9 "person.png"
#	cd ../examples/image_variants/V9_stripedshirt_jacket_hat/ && ids image upload 10 "person.png"
