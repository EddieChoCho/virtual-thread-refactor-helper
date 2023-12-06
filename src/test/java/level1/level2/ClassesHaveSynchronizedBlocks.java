package level1.level2;

class ClassHasSynchronizedBlockUsingThisReference {
    public void syncBlock() {
        synchronized (this) {
            System.out.println("hello");
        }
    }
}

class ClassHasSynchronizedBlockUsingObject {
    private Object object;

    public void syncBlock() {
        synchronized (object) {
            System.out.println("hello");
        }
    }

    public void syncBlock2() {
        synchronized (object) {
            System.out.println("hello");
        }
        System.out.println("outside synchronized block");
    }
}

class ComponentClass {

}

class ClassHasSynchronizedBlockUsingComponent {

    private ComponentClass componentClass;

    public void syncBlock() {
        synchronized (componentClass) {
            System.out.println("hello");
        }
    }
}

class ClassHasSynchronizedBlockUsingClass {

    public void syncBlock() {
        synchronized (ClassHasSynchronizedBlockUsingClass.class) {
            System.out.println("hello");
        }
    }
}

class ClassType {

}

class ClassHasSynchronizedBlockUsingOtherClass {

    public void syncBlock() {
        synchronized (ClassType.class) {
            System.out.println("hello");
        }
    }
}

