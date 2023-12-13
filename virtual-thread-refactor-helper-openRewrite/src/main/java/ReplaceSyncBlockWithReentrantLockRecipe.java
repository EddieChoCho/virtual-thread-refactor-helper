import lombok.Value;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.List;
import java.util.stream.Collectors;

@Value
public class ReplaceSyncBlockWithReentrantLockRecipe extends Recipe {


    @Override
    public String getDisplayName() {
        return "Replace synchronized blocks with reentrantLocks";
    }

    @Override
    public String getDescription() {
        return "A virtual thread is pinned when it runs code inside a synchronized block or method. " +
                "We should replace them with reentrantLocks if there are some I/O operations in the synchronized block or method.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        // getVisitor() should always return a new instance of the visitor to avoid any state leaking between cycles
        return new ReplaceSyncBlockWithReentrantLockVisitor();
    }

    public class ReplaceSyncBlockWithReentrantLockVisitor extends JavaIsoVisitor<ExecutionContext> {

        private final JavaTemplate importTemplate =
                JavaTemplate.builder(" ")
                        .imports("java.util.concurrent.locks.ReentrantLock")
                        .build();

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
            J.Identifier identifier = classDecl.getName();
            if (identifier.getSimpleName().endsWith("Tests") || identifier.getSimpleName().endsWith("Test")) {
                return classDecl;
            }

            List<J.MethodDeclaration> syncMethods = classDecl.getBody().getStatements().stream()
                    .filter(J.MethodDeclaration.class::isInstance)
                    .map(J.MethodDeclaration.class::cast)
                    .filter(method -> method.hasModifier(J.Modifier.Type.Synchronized))
                    .toList();

            if (!syncMethods.isEmpty()) {
                for (J.MethodDeclaration methodDeclaration : syncMethods) {
                    J.MethodDeclaration modifiedMethod = this.refactorSyncMethodWithReentrantLock(methodDeclaration);
                    classDecl = this.refactorClassDeclarationDeclBody(classDecl, modifiedMethod);
                }
            }

            //TODO: refactor synchronized blocks

            return classDecl;

        }

        private J.MethodDeclaration refactorSyncMethodWithReentrantLock(J.MethodDeclaration methodDeclaration) {
            //TODO: add reentrantLock
            List<J.Modifier> modifiers = methodDeclaration.getModifiers().stream()
                    .filter(modifier -> modifier.getType() != J.Modifier.Type.Synchronized)
                    .collect(Collectors.toList());
            return methodDeclaration.withModifiers(modifiers);
        }

        private J.ClassDeclaration refactorClassDeclarationDeclBody(J.ClassDeclaration classDecl, J.MethodDeclaration modifiedMethod) {
            J.Block body = classDecl.getBody();
            List<Statement> statements = body.getStatements();
            int index = statements.indexOf(modifiedMethod);
            statements.remove(index);
            statements.add(index, modifiedMethod);
            J.Block newBody = body.withStatements(statements);
            return classDecl.withBody(newBody);
        }

    }
}
