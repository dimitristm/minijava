GEN_DIR = javacc-gen
BUILD_DIR = build

all: compile

compile: $(BUILD_DIR)/Main.class

$(BUILD_DIR)/Main.class: Main.java minijava.jj
	mkdir -p $(GEN_DIR) $(BUILD_DIR)
	java -jar jtb132di.jar -te minijava.jj
	java -jar javacc5.jar -OUTPUT_DIRECTORY=$(GEN_DIR) minijava-jtb.jj
	javac --release 17 -d $(BUILD_DIR) -sourcepath . $(GEN_DIR)/*.java Main.java

run: compile
	java -cp $(BUILD_DIR) Main Example.java

clean:
	rm -rf $(GEN_DIR) $(BUILD_DIR) syntaxtree visitor minijava-jtb.jj