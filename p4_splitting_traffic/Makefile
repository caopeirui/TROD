BUILD=$(SDE)/pkgsrc/p4-build/
CWD=$(shell pwd)

all: target

configure:
	cd $(BUILD);\
	./configure --prefix=$(SDE_INSTALL) --with-tofino --enable-thrift P4_NAME=thold_split P4_PATH=$(CWD)/p4src/main.p4;\

target:
	cd $(BUILD);\
	make -j6 -B;\
	make install

run:
	$(SDE)/run_switchd.sh -p thold_split -c $(CWD)/targets/tofino/thold_split.conf

graph:
	p4c-tofino ./p4src/main.p4 --create-graphs -o ./output
