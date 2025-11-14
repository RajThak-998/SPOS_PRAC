starting only with the two source files:
- `AddNative.java`
- `AddLib.c`

---

## 🪜 Step-by-Step Commands

### 1 Compile the Java file and generate JNI header
```bash
javac -h . AddNative.java

### Compile the C file into a shared library (.so)

```bash
gcc -fPIC -I"$(dirname $(readlink -f $(which javac)))/../include" \
    -I"$(dirname $(readlink -f $(which javac)))/../include/linux" \
    -shared -o libAddLib.so AddLib.c


### Run the java Program

java -Djava.library.path=. AddNative


# DLL
- dynamic link library
- data link layer
- doubly linked list
- dynamic link loader