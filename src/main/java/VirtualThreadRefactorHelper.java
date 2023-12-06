import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class VirtualThreadRefactorHelper {

    static Logger logger = Logger.getLogger(VirtualThreadRefactorHelper.class.getName());

    private static final String CLASS_LOCK_POSTFIX = "ClassLock";
    private static final String OBJECT_LOCK_POSTFIX = "ObjectLock";
    private static final String FIELD_LOCK_POSTFIX = "Lock";
    private static final ParserConfiguration.LanguageLevel JAVA_VERSION = ParserConfiguration.LanguageLevel.JAVA_17;

    public static void main(String[] args) throws IOException {

        var scanner = new Scanner(System.in);
        logger.info("Please input the projectPath:");
        var projectPath = scanner.nextLine();
        ParserConfiguration parserConfiguration = new ParserConfiguration();
        parserConfiguration.setLanguageLevel(JAVA_VERSION);

        JavaParser javaParser = new JavaParser(parserConfiguration);

        List<CompilationUnit> unitList = parseJavaFiles(javaParser, projectPath);
        for(CompilationUnit cu: unitList){
            boolean refactored = refactorToSupportVirtualThreads(javaParser, unitList, cu);
            if(refactored){
                try (FileOutputStream out = new FileOutputStream(getFilePath(cu))){
                    out.write(cu.toString().getBytes());
                } catch (Exception e){
                    logger.warning("Ｆailed to refactor " +  cu);
                }
            }
        }

        logger.info("Java file modified successfully.");


    }

    public static List<CompilationUnit> parseJavaFiles(JavaParser javaParser, String projectPath) {
        List<CompilationUnit> compilationUnits = new ArrayList<>();

        try (Stream<Path> pathsStream = Files.walk(Paths.get(projectPath), Integer.MAX_VALUE)){
            pathsStream.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.toString().endsWith("Tests.java"))
                    .filter(path -> !path.toString().endsWith("Test.java"))
                    .forEach(path -> {
                        try {
                            ParseResult<CompilationUnit> parseResult = javaParser.parse(path);
                            if (parseResult.isSuccessful()) {
                                compilationUnits.add(parseResult.getResult().orElseThrow());
                            } else {
                                logger.warning("Error parsing file: " +  path);
                                parseResult.getProblems().forEach(error -> logger.warning(error.toString()));
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }

        return compilationUnits;
    }

    public static boolean refactorToSupportVirtualThreads(JavaParser javaParser, List<CompilationUnit> unitList, CompilationUnit cu) throws IOException {
        boolean refactored = false;
        for(TypeDeclaration<?> type : cu.getTypes()){
            for(MethodDeclaration method : type.findAll(MethodDeclaration.class)){
                if (method.isSynchronized()) {
                    refactorSynchronizedMethods(javaParser, cu, type, method);
                    refactored = true;
                }

                if(method.getBody().isPresent()){
                    for(Statement statement : method.getBody().orElseThrow().getStatements()){
                        if(statement.isSynchronizedStmt()){
                            refactorSynchronizeBlocks(javaParser, unitList, cu, type, method, (SynchronizedStmt)statement);
                            refactored = true;
                        }
                    }
                }
            }
        }
        return refactored;
    }

    private static void refactorSynchronizedMethods(JavaParser javaParser, CompilationUnit cu, TypeDeclaration<?> type, MethodDeclaration method) {
        String lockName = getReentrantLockName(type, method.isStatic());
        NodeList<BodyDeclaration<?>> members = type.getMembers();
        if(hasNoLock(members, lockName)){
            members.add(0, createReentrantLock(javaParser, cu, type, method.isStatic()));
        }
        removeSynchronizedKeyword(method);
        wrapMethodBodyWithLockAndUnlockStatements(method, lockName);
    }

    private static void removeSynchronizedKeyword(MethodDeclaration method){
        NodeList<Modifier> modifiers = method.getModifiers();
        Modifier synchronizedKeyword = modifiers.stream()
                .filter(modifier -> modifier.getKeyword() == Modifier.Keyword.SYNCHRONIZED)
                .findFirst()
                .orElseThrow();

        modifiers.remove(synchronizedKeyword);
    }

    private static void wrapMethodBodyWithLockAndUnlockStatements(MethodDeclaration method, String lockName){
        BlockStmt newBody = new BlockStmt();
        newBody.addStatement(new NameExpr(lockName + ".lock()"));
        BlockStmt originalBody = method.getBody().orElseThrow(() -> new IllegalStateException("Method body not found"));

        BlockStmt finallyBlock = new BlockStmt();
        finallyBlock.addStatement(new NameExpr(lockName+ ".unlock()"));
        newBody.addStatement(new TryStmt(originalBody, new NodeList<>(), finallyBlock));
        method.setBody(newBody);
    }

    private static boolean hasNoLock(NodeList<BodyDeclaration<?>> members, String lockName){
        return members.stream()
                .filter(FieldDeclaration.class::isInstance)
                .map(FieldDeclaration.class::cast)
                .map(FieldDeclaration::getVariables)
                .flatMap(Collection::stream)
                .noneMatch(variable -> lockName.equals(variable.getName().asString()));
    }

    private static FieldDeclaration getLock(NodeList<BodyDeclaration<?>> members, String lockName){
        return members.stream()
                .filter(FieldDeclaration.class::isInstance)
                .map(FieldDeclaration.class::cast)
                .filter(field -> field.getVariables().stream()
                        .anyMatch(variable -> lockName.equals(variable.getName().asString())))
                .findFirst()
                .orElseThrow();
    }

    private static String getReentrantLockName(TypeDeclaration<?> type, boolean isStatic){
        String postfix = isStatic ? CLASS_LOCK_POSTFIX : OBJECT_LOCK_POSTFIX;
        String className = type.getName().asString();
        return className.substring(0, 1).toLowerCase() + className.substring(1) + postfix;
    }

    private static String getReentrantLockName(String fieldName){
        return fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1) + FIELD_LOCK_POSTFIX;
    }

    private static FieldDeclaration createReentrantLock(JavaParser javaParser, CompilationUnit cu, TypeDeclaration<?> type, boolean isStatic){
        cu.addImport("java.util.concurrent.locks.ReentrantLock");
        String lockName = getReentrantLockName(type, isStatic);
        return createReentrantLock(javaParser, lockName, isStatic);
    }

    private static FieldDeclaration createReentrantLock(JavaParser javaParser, CompilationUnit cu, String fieldName){
        cu.addImport("java.util.concurrent.locks.ReentrantLock");
        String lockName = getReentrantLockName(fieldName);
        return createReentrantLock(javaParser, lockName, false);
    }

    private static FieldDeclaration createReentrantLock(JavaParser javaParser, String lockName, boolean isStatic){
        FieldDeclaration objectLockField = new FieldDeclaration(new NodeList<>(),
                new VariableDeclarator(javaParser.parseClassOrInterfaceType("ReentrantLock").getResult().orElseThrow(),
                        lockName, new NameExpr("new ReentrantLock()")));
        objectLockField.setModifier(Modifier.Keyword.PRIVATE, true);
        if(isStatic){
            objectLockField.setModifier(Modifier.Keyword.STATIC, true);
        }
        return objectLockField;
    }
    private static void refactorSynchronizeBlocks(JavaParser javaParser, List<CompilationUnit> unitList, CompilationUnit cu, TypeDeclaration<?> type, MethodDeclaration method, SynchronizedStmt statement) throws IOException {
        Expression expression = statement.getExpression();
        if(expression instanceof ThisExpr){
            refactorSynchronizeBlockUsingThis(javaParser, cu, type, method, statement);

        } else if(expression instanceof NameExpr nameExpr){
            refactorSynchronizeBlockUsingField(javaParser, unitList, cu, type, method, statement, nameExpr);
        }
    }

    private static void refactorSynchronizeBlockUsingThis(JavaParser javaParser, CompilationUnit cu, TypeDeclaration<?> type, MethodDeclaration method, SynchronizedStmt statement){
        String lockName = getReentrantLockName(type, false);
        if(hasNoLock(type.getMembers(), lockName)){
            FieldDeclaration lockFiled = createReentrantLock(javaParser, cu, type, false);
            type.getMembers().add(0, lockFiled);
        }
        refactorSyncBlockWithLock(method, statement, lockName);
    }

    private static void refactorSynchronizeBlockUsingField(JavaParser javaParser, List<CompilationUnit> unitList, CompilationUnit cu, TypeDeclaration<?> type, MethodDeclaration method, SynchronizedStmt statement, NameExpr nameExpr) throws IOException {
        SimpleName lockObjectName = nameExpr.getName();
        FieldDeclaration lockObjectField = type.getMembers().stream()
                .filter(FieldDeclaration.class::isInstance)
                .map(FieldDeclaration.class::cast)
                .filter(member -> member.getVariables().stream().anyMatch(variableDeclarator -> variableDeclarator.getName().equals(lockObjectName)))
                .findFirst().orElseThrow();
        ClassOrInterfaceType typeOfLockObjectField = (ClassOrInterfaceType) lockObjectField.getElementType();

        Optional<CompilationUnit> unitDefinedType = unitList.stream()
                .filter(unit -> unit.getTypes().stream()
                        .anyMatch(typeDeclaration -> typeDeclaration.getName().equals(typeOfLockObjectField.getName())))
                .findFirst();

        if(unitDefinedType.isPresent()){
            refactorSynchronizeBlockUsingComponent(javaParser, unitDefinedType.orElseThrow(), typeOfLockObjectField, method, statement, lockObjectName);
        } else {
            refactorSynchronizeBlockUsingObject(javaParser, cu, type, method, statement, lockObjectName);
        }
    }

    private static void refactorSynchronizeBlockUsingComponent(JavaParser javaParser, CompilationUnit unitDefinedType, ClassOrInterfaceType typeOfLockObjectField, MethodDeclaration method, SynchronizedStmt statement, SimpleName lockObjectName) throws IOException {
        TypeDeclaration<?> typeDeclarationOfLockObjectField = unitDefinedType.getTypes().stream()
                .filter(typeDeclaration -> typeDeclaration.getName().equals(typeOfLockObjectField.getName()))
                .findFirst()
                .orElseThrow();

        String lockName = getReentrantLockName(typeDeclarationOfLockObjectField, false);
        final FieldDeclaration lockFiled;
        if(hasNoLock(typeDeclarationOfLockObjectField.getMembers(), lockName)){
            lockFiled = createReentrantLock(javaParser, unitDefinedType, typeDeclarationOfLockObjectField, false);
            typeDeclarationOfLockObjectField.getMembers().add(0, lockFiled);
        } else {
            lockFiled = getLock(typeDeclarationOfLockObjectField.getMembers(), lockName);
        }
        lockFiled.removeModifier(Modifier.Keyword.PRIVATE);
        lockFiled.setModifier(Modifier.Keyword.PUBLIC, true);
        try (FileOutputStream out = new FileOutputStream(getFilePath(unitDefinedType))){
            out.write(unitDefinedType.toString().getBytes());
        }

        refactorSyncBlockWithLock(method, statement, lockObjectName + "." + lockName);
    }

    private static void refactorSynchronizeBlockUsingObject(JavaParser javaParser, CompilationUnit cu, TypeDeclaration<?> type, MethodDeclaration method, SynchronizedStmt statement, SimpleName lockObjectName) {
        String lockName = getReentrantLockName(lockObjectName.asString());
        if(hasNoLock(type.getMembers(), lockName)){
            FieldDeclaration lockFiled = createReentrantLock(javaParser, cu, lockObjectName.asString());
            type.getMembers().add(0, lockFiled);
        }
        refactorSyncBlockWithLock(method, statement, lockName);
    }

    private static void refactorSyncBlockWithLock(MethodDeclaration method, SynchronizedStmt statement, String lockName){
        BlockStmt statementInsideBlock = new BlockStmt();
        statementInsideBlock.addStatement(statement.getBody().getStatements().get(0));

        BlockStmt newBody = new BlockStmt();
        newBody.addStatement(new NameExpr(lockName + ".lock()"));
        BlockStmt finallyBlock = new BlockStmt();
        finallyBlock.addStatement(new NameExpr(lockName+ ".unlock()"));
        newBody.addStatement(new TryStmt(statementInsideBlock, new NodeList<>(), finallyBlock));
        method.getBody().orElseThrow().replace(statement, newBody);
    }

    private static String getFilePath(CompilationUnit cu){
        return cu.getStorage().orElseThrow().getDirectory() + "/" + cu.getStorage().orElseThrow().getFileName();
    }

}
