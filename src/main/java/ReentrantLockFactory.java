import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.NameExpr;

public class ReentrantLockFactory {

    private static final String CLASS_LOCK_POSTFIX = "ClassLock";
    private static final String OBJECT_LOCK_POSTFIX = "ObjectLock";
    private static final String FIELD_LOCK_POSTFIX = "Lock";

    public static String getReentrantLockName(TypeDeclaration<?> type, boolean isStatic) {
        String postfix = isStatic ? CLASS_LOCK_POSTFIX : OBJECT_LOCK_POSTFIX;
        String className = type.getName().asString();
        return className.substring(0, 1).toLowerCase() + className.substring(1) + postfix;
    }

    public static String getReentrantLockName(String fieldName) {
        return fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1) + FIELD_LOCK_POSTFIX;
    }

    public static FieldDeclaration createReentrantLock(JavaParser javaParser, CompilationUnit cu, TypeDeclaration<?> type, boolean isStatic) {
        cu.addImport("java.util.concurrent.locks.ReentrantLock");
        String lockName = getReentrantLockName(type, isStatic);
        return createReentrantLock(javaParser, lockName, isStatic);
    }

    public static FieldDeclaration createReentrantLock(JavaParser javaParser, CompilationUnit cu, String fieldName) {
        cu.addImport("java.util.concurrent.locks.ReentrantLock");
        String lockName = getReentrantLockName(fieldName);
        return createReentrantLock(javaParser, lockName, false);
    }

    public static FieldDeclaration createReentrantLock(JavaParser javaParser, String lockName, boolean isStatic) {
        FieldDeclaration objectLockField = new FieldDeclaration(new NodeList<>(),
                new VariableDeclarator(javaParser.parseClassOrInterfaceType("ReentrantLock").getResult().orElseThrow(),
                        lockName, new NameExpr("new ReentrantLock()")));
        objectLockField.setModifier(Modifier.Keyword.PRIVATE, true);
        if (isStatic) {
            objectLockField.setModifier(Modifier.Keyword.STATIC, true);
        }
        return objectLockField;
    }
}
