package dev.mattramotar.storex.repository.compiler.ksp


import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSVisitorVoid
import dev.mattramotar.storex.repository.runtime.OperationType
import dev.mattramotar.storex.repository.runtime.annotations.RepositoryConfig

class RepositoryVisitor(
    private val codeGenerator: CodeGenerator,
    private val packageName: String?,
    private val logger: KSPLogger
) : KSVisitorVoid() {
    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
        val annotation = classDeclaration.annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == RepositoryConfig::class.qualifiedName
        } ?: return


        val repositoryName = annotation.getArg<String>("name")
        val keyType = annotation.getArg<KSType>("keyType")
        val queryType = annotation.getArg<KSType>("queryType")
        val propertiesType = annotation.getArg<KSType>("propertiesType")
        val nodeType = annotation.getArg<KSType>("nodeType")
        val compositeType = annotation.getArg<KSType>("compositeType")
        val errorType = annotation.getArg<KSType>("errorType")

        val operationsArg = annotation.arguments.first { it.name?.asString() == "operations" }.value as List<*>
        val operations = operationsArg.map { arg ->
            val enumEntry = arg as KSType
            OperationType.valueOf(enumEntry.declaration.simpleName.asString())
        }.toTypedArray()

        val packageName = this.packageName ?: classDeclaration.packageName.asString()

        generateRepositoryInterface(
            packageName = packageName,
            repositoryName = repositoryName,
            keyType = keyType,
            queryType = queryType,
            propertiesType = propertiesType,
            nodeType = nodeType,
            compositeType = compositeType,
            errorType = errorType,
            operations = operations
        )
        generateBuilder(
            packageName = packageName,
            repositoryName = repositoryName,
            keyType = keyType,
            queryType = queryType,
            propertiesType = propertiesType,
            nodeType = nodeType,
            compositeType = compositeType,
            errorType = errorType,
            operations = operations
        )
    }

    private fun generateRepositoryInterface(
        packageName: String,
        repositoryName: String,
        keyType: KSType,
        queryType: KSType,
        propertiesType: KSType,
        nodeType: KSType,
        compositeType: KSType,
        errorType: KSType,
        operations: Array<OperationType>
    ) {

        val file = codeGenerator.createNewFile(
            Dependencies(false),
            packageName,
            repositoryName
        )

        file.writer().use { writer ->
            writer.write("package $packageName\n\n")

            writer.write("import dev.mattramotar.storex.repository.runtime.operations.query.*\n")
            writer.write("import dev.mattramotar.storex.repository.runtime.operations.mutation.*\n")
            writer.write("import dev.mattramotar.storex.repository.runtime.operations.bulk.*\n")
            writer.write("import dev.mattramotar.storex.repository.runtime.operations.observation.*\n")

            writer.write("import ${keyType.declaration.qualifiedName!!.asString()}\n")
            writer.write("import ${queryType.declaration.qualifiedName!!.asString()}\n")
            writer.write("import ${propertiesType.declaration.qualifiedName!!.asString()}\n")
            writer.write("import ${nodeType.declaration.qualifiedName!!.asString()}\n")
            writer.write("import ${compositeType.declaration.qualifiedName!!.asString()}\n")
            writer.write("import ${errorType.declaration.qualifiedName!!.asString()}\n")

//            var interfaceGenerics = "<Key, "
//            if (queryType.declaration.simpleName.asString() != "Any") {
//                interfaceGenerics+= "Query, "
//            }
//
//            if (propertiesType.declaration.simpleName.asString() != "Any") {
//                interfaceGenerics+= "Properties, "
//            }
//
//            interfaceGenerics+= "Node"
//
//            if (compositeType.declaration.simpleName.asString() != "Any") {
//                interfaceGenerics+= ", Composite"
//            }
//
//            if (errorType.declaration.simpleName.asString() != "Throwable") {
//                interfaceGenerics += ", Error"
//            }
//
//            interfaceGenerics+= ">"

            writer.write("interface $repositoryName : ")
            val operationTypes =
                operations.map {
                    it.operationType(
                        keyType,
                        queryType,
                        propertiesType,
                        nodeType,
                        compositeType,
                        errorType
                    )
                }
            writer.write(operationTypes.joinToString(",\n   "))
            writer.write("\n")
        }
    }

    private fun generateBuilder(
        packageName: String,
        repositoryName: String,
        keyType: KSType,
        queryType: KSType,
        propertiesType: KSType,
        nodeType: KSType,
        compositeType: KSType,
        errorType: KSType,
        operations: Array<OperationType>
    ) {
        val builderName = "${repositoryName}Builder"

        val file = codeGenerator.createNewFile(
            Dependencies(false),
            packageName,
            builderName
        )

        file.writer().use { writer ->
            writer.write("package $packageName\n\n")

            writer.write("import dev.mattramotar.storex.repository.runtime.operations.query.*\n")
            writer.write("import dev.mattramotar.storex.repository.runtime.operations.mutation.*\n")
            writer.write("import dev.mattramotar.storex.repository.runtime.operations.bulk.*\n")
            writer.write("import dev.mattramotar.storex.repository.runtime.operations.observation.*\n")

            writer.write("import ${keyType.declaration.qualifiedName!!.asString()}\n")
            writer.write("import ${queryType.declaration.qualifiedName!!.asString()}\n")
            writer.write("import ${propertiesType.declaration.qualifiedName!!.asString()}\n")
            writer.write("import ${nodeType.declaration.qualifiedName!!.asString()}\n")
            writer.write("import ${compositeType.declaration.qualifiedName!!.asString()}\n")
            writer.write("import ${errorType.declaration.qualifiedName!!.asString()}\n")

            writer.write("class $builderName {\n")

            operations.forEach {
                val fieldName = it.fieldName()
                val operationType = it.operationType(
                    keyType = keyType,
                    queryType = queryType,
                    propertiesType = propertiesType,
                    nodeType = nodeType,
                    compositeType = compositeType,
                    errorType = errorType
                )
                writer.write("    private var $fieldName: $operationType? = null\n")
            }

            writer.write("\n")

            operations.forEach {
                val fieldName = it.fieldName()
                val operationType = it.operationType(
                    keyType = keyType,
                    queryType = queryType,
                    propertiesType = propertiesType,
                    nodeType = nodeType,
                    compositeType = compositeType,
                    errorType = errorType
                )
                val methodName = "with${fieldName.replaceFirstChar { char -> char.uppercase() }}"
                writer.write("    fun $methodName(operation: $operationType) = apply { this.$fieldName = operation }\n")
            }

            writer.write("\n    fun build(): $repositoryName {\n")
            operations.forEach {
                val fieldName = it.fieldName()
                writer.write("        val $fieldName = $fieldName ?: error(\"$fieldName not provided\")\n")
            }

            writer.write("        return object : $repositoryName,\n")
            operations.forEachIndexed { index, operation ->
                val fieldName = operation.fieldName()
                val delim = if (index < operations.size - 1) "," else ""
                writer.write(
                    "            ${
                        operation.operationType(
                            keyType = keyType,
                            queryType = queryType,
                            propertiesType = propertiesType,
                            nodeType = nodeType,
                            compositeType = compositeType,
                            errorType = errorType
                        )
                    } by $fieldName$delim\n"
                )
            }
            writer.write("        {}\n")
            writer.write("    }\n")

            writer.write("}\n")
        }
    }

    private inline fun <reified T> KSAnnotation.getArg(name: String): T {
        return arguments.first { it.name?.asString() == name }.value as T
    }

    private fun OperationType.fieldName(): String = when (this) {
        OperationType.FindAll -> "findAllOperation"
        OperationType.FindManyComposite -> "findManyCompositeOperation"
        OperationType.FindOneComposite -> "findOneCompositeOperation"
        OperationType.FindOneOperation -> "findOneOperation"
        OperationType.QueryManyComposite -> "queryManyCompositeOperation"
        OperationType.QueryMany -> "queryManyOperation"
        OperationType.QueryOneComposite -> "queryOneCompositeOperation"
        OperationType.QueryOne -> "queryOneOperation"
        OperationType.DeleteOne -> "deleteOneOperation"
        OperationType.InsertOne -> "insertOneOperation"
        OperationType.ReplaceOne -> "replaceOneOperation"
        OperationType.UpdateOne -> "updateOneOperation"
        OperationType.UpsertOne -> "upsertOneOperation"
        OperationType.ObserveAll -> "observeAllOperation"
        OperationType.ObserveManyComposite -> "observeManyCompositeOperation"
        OperationType.ObserveMany -> "observeManyOperation"
        OperationType.ObserveOneComposite -> "observeOneCompositeOperation"
        OperationType.ObserveOne -> "observeOneOperation"
        OperationType.DeleteAll -> "deleteAllOperation"
        OperationType.DeleteMany -> "deleteManyOperation"
        OperationType.InsertMany -> "insertManyOperation"
    }

    private fun OperationType.operationType(
        keyType: KSType,
        queryType: KSType,
        propertiesType: KSType,
        nodeType: KSType,
        compositeType: KSType,
        errorType: KSType,
    ): String {

        val findAllOperation = "FindAllOperation<${nodeType}, ${errorType}>"
        val findManyCompositeOperation = "FindManyComposite<${keyType}, ${compositeType}, ${errorType}>"
        val findOneCompositeOperation = "FindOneCompositeOperation<${keyType}, ${compositeType}, ${errorType}>"
        val findOneOperation = "FindOneOperation<${keyType}, ${nodeType}, ${errorType}>"
        val queryManyCompositeOperation = "QueryManyCompositeOperation<${queryType}, ${compositeType}, ${errorType}>"
        val queryManyOperation = "QueryManyOperation<${queryType}, ${nodeType}, ${errorType}>"
        val queryOneCompositeOperation = "QueryOneCompositeOperation<${queryType}, ${compositeType}, ${errorType}>"
        val queryOneOperation = "QueryOneOperation<${queryType}, ${nodeType}, ${errorType}>"
        val deleteOneOperation = "DeleteOneOperation<${keyType}, ${errorType}>"
        val insertOneOperation = "InsertOneOperation<${propertiesType}, ${nodeType}, ${errorType}>"
        val replaceOneOperation = "ReplaceOneOperation<${nodeType}, ${errorType}>"
        val updateOneOperation = "UpdateOneOperation<${nodeType}, ${errorType}>"
        val upsertOneOperation = "UpsertOneOperation<${propertiesType}, ${nodeType}, ${errorType}>"
        val observeAllOperation = "ObserveAllOperation<${nodeType}, ${errorType}>"
        val observeManyCompositeOperation = "ObserveManyCompositeOperation<${keyType}, ${compositeType}, ${errorType}>"
        val observeManyOperation = "ObserveManyOperation<${keyType}, ${nodeType}, ${errorType}>"
        val observeOneCompositeOperation = "ObserveOneCompositeOperation<${keyType}, ${compositeType}, ${errorType}>"
        val observeOneOperation = "ObserveOneOperation<${keyType}, ${nodeType}, ${errorType}>"
        val deleteAllOperation = "DeleteAllOperation<${errorType}>"
        val deleteManyOperation = "DeleteManyOperation<${keyType}, ${errorType}>"
        val insertManyOperation = "InsertManyOperation<${propertiesType}, ${nodeType}, ${errorType}>"

        return when (this) {
            OperationType.FindAll -> findAllOperation
            OperationType.FindManyComposite -> findManyCompositeOperation
            OperationType.FindOneComposite -> findOneCompositeOperation
            OperationType.FindOneOperation -> findOneOperation
            OperationType.QueryManyComposite -> queryManyCompositeOperation
            OperationType.QueryMany -> queryManyOperation
            OperationType.QueryOneComposite -> queryOneCompositeOperation
            OperationType.QueryOne -> queryOneOperation
            OperationType.DeleteOne -> deleteOneOperation
            OperationType.InsertOne -> insertOneOperation
            OperationType.ReplaceOne -> replaceOneOperation
            OperationType.UpdateOne -> updateOneOperation
            OperationType.UpsertOne -> upsertOneOperation
            OperationType.ObserveAll -> observeAllOperation
            OperationType.ObserveManyComposite -> observeManyCompositeOperation
            OperationType.ObserveMany -> observeManyOperation
            OperationType.ObserveOneComposite -> observeOneCompositeOperation
            OperationType.ObserveOne -> observeOneOperation
            OperationType.DeleteAll -> deleteAllOperation
            OperationType.DeleteMany -> deleteManyOperation
            OperationType.InsertMany -> insertManyOperation
        }
    }
}
