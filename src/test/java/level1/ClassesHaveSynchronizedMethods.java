package level1;

class ClassesHaveStaticAndNonStaticSynchronizedMethods {

    private synchronized void hi() {
        System.out.println("hi");
    }

    public synchronized void hello() {
        System.out.println("hello");
    }

    public static synchronized void yo() {
        System.out.println("yo");
    }
}

class ClassesHaveOnlyNonStaticSynchronizedMethods {

    private synchronized void hi_2() {
        System.out.println("hi");
    }

    public synchronized void hello_2() {
        System.out.println("hello");
    }
}

