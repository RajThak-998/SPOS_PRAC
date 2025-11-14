public class AddNative {
    // Declaration of native method
    public native int add(int a, int b);

    // Load the shared library (libAddLib.so)
    static {
        System.loadLibrary("AddLib");
    }

    // Test it
    public static void main(String[] args) {
        AddNative obj = new AddNative();
        int result = obj.add(10, 25);
        System.out.println("Result of addition = " + result);
    }
}

