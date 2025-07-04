/*
 * Copyright 1999-2017 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.druid.sql.dialect.hive.visitor;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.ast.SQLAdhocTableSource;
import com.alibaba.druid.sql.ast.SQLCommentHint;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLName;
import com.alibaba.druid.sql.ast.SQLObject;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOpExpr;
import com.alibaba.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.dialect.hive.ast.HiveAddJarStatement;
import com.alibaba.druid.sql.dialect.hive.ast.HiveInsert;
import com.alibaba.druid.sql.dialect.hive.ast.HiveInsertStatement;
import com.alibaba.druid.sql.dialect.hive.ast.HiveMultiInsertStatement;
import com.alibaba.druid.sql.dialect.hive.stmt.HiveCreateFunctionStatement;
import com.alibaba.druid.sql.dialect.hive.stmt.HiveCreateTableStatement;
import com.alibaba.druid.sql.dialect.hive.stmt.HiveLoadDataStatement;
import com.alibaba.druid.sql.dialect.hive.stmt.HiveMsckRepairStatement;
import com.alibaba.druid.sql.visitor.SQLASTOutputVisitor;

import java.util.List;
import java.util.Map;

public class HiveOutputVisitor extends SQLASTOutputVisitor implements HiveASTVisitor {
    public HiveOutputVisitor(StringBuilder appender) {
        super(appender, DbType.hive);
    }

    public HiveOutputVisitor(StringBuilder appender, DbType dbType) {
        super(appender, dbType);
    }

    public HiveOutputVisitor(StringBuilder appender, boolean parameterized) {
        super(appender, DbType.hive, parameterized);
    }

    @Override
    public boolean visit(HiveInsert x) {
        if (x.hasBeforeComment()) {
            printlnComments(x.getBeforeCommentsDirect());
        }

        if (x.getInsertBeforeCommentsDirect() != null) {
            printlnComments(x.getInsertBeforeCommentsDirect());
        }

        SQLExprTableSource tableSource = x.getTableSource();

        if (tableSource != null) {
            if (x.isOverwrite()) {
                print0(ucase ? "INSERT OVERWRITE TABLE " : "insert overwrite table ");
            } else {
                print0(ucase ? "INSERT INTO TABLE " : "insert into table ");
            }

            tableSource.accept(this);
        }

        List<SQLAssignItem> partitions = x.getPartitions();
        if (partitions != null) {
            int partitionsSize = partitions.size();
            if (partitionsSize > 0) {
                print0(ucase ? " PARTITION (" : " partition (");
                for (int i = 0; i < partitionsSize; ++i) {
                    if (i != 0) {
                        print0(", ");
                    }

                    SQLAssignItem assign = partitions.get(i);
                    assign.getTarget().accept(this);

                    if (assign.getValue() != null) {
                        print('=');
                        assign.getValue().accept(this);
                    }
                }
                print(')');
            }
            println();
        }

        SQLSelect select = x.getQuery();
        List<SQLInsertStatement.ValuesClause> valuesList = x.getValuesList();
        if (select != null) {
            select.accept(this);
        } else if (!valuesList.isEmpty()) {
            print0(ucase ? "VALUES " : "values ");
            printAndAccept(valuesList, ", ");
        }

        return false;
    }

    public boolean visit(SQLExternalRecordFormat x) {
        return hiveVisit(x);
    }

    @Override
    public boolean visit(HiveMultiInsertStatement x) {
        SQLWithSubqueryClause with = x.getWith();
        if (with != null) {
            visit(with);
            println();
        }

        SQLTableSource from = x.getFrom();
        if (x.getFrom() != null) {
            if (from instanceof SQLSubqueryTableSource) {
                SQLSelect select = ((SQLSubqueryTableSource) from).getSelect();
                print0(ucase ? "FROM (" : "from (");
                this.indentCount++;
                println();
                select.accept(this);
                this.indentCount--;
                println();
                print0(") ");
                String alias = x.getFrom().getAlias();
                if (alias != null) {
                    print0(alias);
                }
            } else {
                print0(ucase ? "FROM " : "from ");
                from.accept(this);
            }
            println();
        }

        for (int i = 0; i < x.getItems().size(); ++i) {
            HiveInsert insert = x.getItems().get(i);
            if (i != 0) {
                println();
            }
            insert.accept(this);
        }
        return false;
    }

    public boolean visit(HiveInsertStatement x) {
        if (x.hasBeforeComment()) {
            printlnComments(x.getBeforeCommentsDirect());
        }

        SQLWithSubqueryClause with = x.getWith();
        if (with != null) {
            visit(with);
            println();
        }

        List<String> insertBeforeComments = x.getInsertBeforeCommentsDirect();
        if (insertBeforeComments != null) {
            printlnComments(insertBeforeComments);
        }

        if (x.isOverwrite()) {
            print0(ucase ? "INSERT OVERWRITE TABLE " : "insert overwrite table ");
        } else {
            print0(ucase ? "INSERT INTO TABLE " : "insert into table ");
        }
        x.getTableSource().accept(this);

        List<SQLAssignItem> partitions = x.getPartitions();
        int partitionSize = partitions.size();
        if (partitionSize > 0) {
            print0(ucase ? " PARTITION (" : " partition (");
            for (int i = 0; i < partitionSize; ++i) {
                if (i != 0) {
                    print0(", ");
                }

                SQLAssignItem assign = partitions.get(i);
                assign.getTarget().accept(this);

                if (assign.getValue() != null) {
                    print('=');
                    assign.getValue().accept(this);
                }
            }
            print(')');
        }

        List<SQLExpr> columns = x.getColumns();
        if (columns.size() > 0) {
            print(" (");
            printAndAccept(columns, ", ");
            print(')');
        }

        if (x.isIfNotExists()) {
            print0(ucase ? " IF NOT EXISTS" : " if not exists");
        }
        println();

        SQLSelect select = x.getQuery();
        List<SQLInsertStatement.ValuesClause> valuesList = x.getValuesList();
        if (select != null) {
            select.accept(this);
        } else if (!valuesList.isEmpty()) {
            print0(ucase ? "VALUES " : "values ");
            printAndAccept(valuesList, ", ");
        }

        return false;
    }

    public boolean visit(SQLMergeStatement.WhenUpdate x) {
        print0(ucase ? "WHEN" : "when");
        if (x.isNot()) {
            print0(ucase ? " NOT" : " not");
        }
        print0(ucase ? " MATCHED " : " matched ");
        this.indentCount++;
        SQLExpr where = x.getWhere();
        if (where != null) {
            this.indentCount++;
            if (SQLBinaryOpExpr.isAnd(where)) {
                println();
            } else {
                print(' ');
            }

            print0(ucase ? "AND " : "and ");

            printExpr(where, parameterized);
            this.indentCount--;
            println();
        }
        print0(ucase ? "UPDATE SET " : "update set ");
        printAndAccept(x.getItems(), ", ");
        this.indentCount--;

        return false;
    }

    @Override
    public boolean visit(HiveCreateFunctionStatement x) {
        if (x.isTemporary()) {
            print0(ucase ? "CREATE TEMPORARY FUNCTION " : "create temporary function ");
        } else {
            print0(ucase ? "CREATE FUNCTION " : "create function ");
        }
        x.getName().accept(this);

        SQLExpr className = x.getClassName();
        if (className != null) {
            print0(ucase ? " AS " : " as ");
            className.accept(this);
        }

        indentCount++;
        SQLExpr location = x.getLocation();
        HiveCreateFunctionStatement.ResourceType resourceType = x.getResourceType();

        if (location != null) {
            println();

            if (resourceType != null) {
                print0(ucase ? "USING " : "using ");
                print0(resourceType.name());
                print(' ');
            } else {
                print0(ucase ? "LOCATION " : "location ");
            }
            location.accept(this);
        }

        String code = x.getCode();
        if (code != null) {
            println();
            print0(ucase ? "USING" : "using");
            print0(code);
        }

        SQLExpr symbol = x.getSymbol();
        if (symbol != null) {
            println();
            print0(ucase ? "SYMBOL = " : "symbol = ");
            symbol.accept(this);
        }

        indentCount--;

        return false;
    }

    @Override
    public boolean visit(HiveLoadDataStatement x) {
        print0(ucase ? "LOAD DATA " : "load data ");

        if (x.isLocal()) {
            print0(ucase ? "LOCAL " : "local ");
        }

        print0(ucase ? "INPATH " : "inpath ");
        x.getInpath().accept(this);

        if (x.isOverwrite()) {
            print0(ucase ? " OVERWRITE INTO TABLE " : " overwrite into table ");
        } else {
            print0(ucase ? " INTO TABLE " : " into table ");
        }
        x.getInto().accept(this);

        if (x.getPartition().size() > 0) {
            print0(ucase ? " PARTITION (" : " partition (");
            printAndAccept(x.getPartition(), ", ");
            print(')');
        }

        return false;
    }

    @Override
    public boolean visit(HiveMsckRepairStatement x) {
        final List<SQLCommentHint> headHints = x.getHeadHintsDirect();
        if (headHints != null) {
            for (SQLCommentHint hint : headHints) {
                hint.accept(this);
                println();
            }
        }
        print0(ucase ? "MSCK REPAIR" : "msck repair");

        SQLName database = x.getDatabase();
        if (database != null) {
            print0(ucase ? " DATABASE " : " database ");
            database.accept(this);
        }

        SQLExprTableSource table = x.getTable();
        if (table != null) {
            print0(ucase ? " TABLE " : " table ");
            table.accept(this);
        }

        if (x.isAddPartitions()) {
            print0(ucase ? " ADD PARTITIONS" : " add partitions");
        }
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableExchangePartition x) {
        print0(ucase ? "EXCHANGE PARTITION (" : "exchange partition (");
        printAndAccept(x.getPartitions(), ", ");
        print0(ucase ? ") WITH TABLE " : ") with table ");
        x.getTable().accept(this);

        Boolean validation = x.getValidation();
        if (validation != null) {
            if (validation) {
                print0(ucase ? " WITH VALIDATION" : " with validation");
            } else {
                print0(ucase ? " WITHOUT VALIDATION" : " without validation");
            }
        }

        return false;
    }

    @Override
    public boolean visit(SQLCreateIndexStatement x) {
        print0(ucase ? "CREATE " : "create ");
        print0(ucase ? "INDEX " : "index ");

        x.getName().accept(this);
        print0(ucase ? " ON TABLE " : " on table ");
        x.getTable().accept(this);
        print0(" (");
        printAndAccept(x.getItems(), ", ");
        print(')');

        String type = x.getType();
        if (type != null) {
            print0(ucase ? " AS " : " as ");
            print0(type);
        }

        if (x.isDeferedRebuild()) {
            print0(ucase ? " WITH DEFERRED REBUILD" : " with deferred rebuild");
        }

        if (x.getProperties().size() > 0) {
            print0(ucase ? " IDXPROPERTIES (" : " idxproperties (");
            printAndAccept(x.getProperties(), ", ");
            print(')');
        }

        // for mysql
        String using = x.getUsing();
        if (using != null) {
            print0(ucase ? " USING " : " using ");
            print0(using);
        }

        SQLExpr comment = x.getComment();
        if (comment != null) {
            print0(ucase ? " COMMENT " : " comment ");
            comment.accept(this);
        }

        final SQLTableSource in = x.getIn();
        if (in != null) {
            print0(ucase ? " IN TABLE " : " in table ");
            in.accept(this);
        }

        final SQLExternalRecordFormat format = x.getRowFormat();
        if (format != null) {
            println();
            print0(ucase ? "ROW FORMAT DELIMITED " : "row rowFormat delimited ");
            visit(format);
        }

        final SQLName storedAs = x.getStoredAs();
        if (storedAs != null) {
            print0(ucase ? " STORED BY " : " stored by ");
            storedAs.accept(this);
        }

        if (x.getTableProperties().size() > 0) {
            print0(ucase ? " TBLPROPERTIES (" : " tblproperties (");
            printAndAccept(x.getTableProperties(), ", ");
            print(')');
        }

        return false;
    }

    public boolean visit(SQLCharExpr x, boolean parameterized) {
        String text = x.getText();
        if (text == null) {
            print0(ucase ? "NULL" : "null");
        } else {
            StringBuilder buf = new StringBuilder(text.length() + 2);
            buf.append('\'');
            for (int i = 0; i < text.length(); ++i) {
                char ch = text.charAt(i);
                switch (ch) {
                    case '\\':
                        buf.append("\\\\");
                        break;
                    case '\'':
                        buf.append("\\'");
                        break;
                    case '\0':
                        buf.append("\\0");
                        break;
                    case '\n':
                        buf.append("\\n");
                        break;
                    case '\r':
                        buf.append("\\r");
                        break;
                    case '\b':
                        buf.append("\\b");
                        break;
                    case '\t':
                        buf.append("\\t");
                        break;
                    default:
                        if (ch == '\u2605') {
                            buf.append("\\u2605");
                        } else if (ch == '\u25bc') {
                            buf.append("\\u25bc");
                        } else {
                            buf.append(ch);
                        }
                        break;
                }
            }
            buf.append('\'');

            print0(buf.toString());
        }

        return false;
    }

    public boolean visit(HiveAddJarStatement x) {
        print0(ucase ? "ADD JAR " : "add jar ");
        print0(x.getPath());
        return false;
    }

    @Override
    protected void printTableOptionsPrefix(SQLCreateTableStatement x) {
        println();
        print0(ucase ? "TBLPROPERTIES (" : "tblproperties (");
        incrementIndent();
        println();
    }

    public boolean visit(HiveCreateTableStatement x) {
        return visit((SQLCreateTableStatement) x);
    }
    @Override
    public boolean visit(SQLCreateTableStatement x) {
        printCreateTable(x, true, true);
        return false;
    }

    protected void printCreateTable(SQLCreateTableStatement x, boolean printSelect,
                                    boolean printCommentAdvance) {
        final SQLObject parent = x.getParent();
        if (x.hasBeforeComment()) {
            printlnComments(x.getBeforeCommentsDirect());
        }
        if (parent instanceof SQLAdhocTableSource) {
            // skip
        } else {
            print0(ucase ? "CREATE " : "create ");
        }
        printCreateTableFeatures(x);
        print0(ucase ? "TABLE " : "table ");
        if (x.isIfNotExists()) {
            print0(ucase ? "IF NOT EXISTS " : "if not exists ");
        }
        printTableSourceExpr(x.getName());
        printTableElements(x.getTableElementList());
        printInherits(x);
        if (x instanceof HiveCreateTableStatement) {
            printUsing((HiveCreateTableStatement) x);
        }
        if (printCommentAdvance) {
            printComment(x.getComment());
        }
        if (x instanceof HiveCreateTableStatement) {
            printMappedBy(((HiveCreateTableStatement) x).getMappedBy());
        }
        printPartitionedBy(x);
        printClusteredBy(x);
        printSortedBy(x.getSortedBy());
        printIntoBuckets(x.getBuckets());
        if (x instanceof HiveCreateTableStatement) {
            printSkewedBy((HiveCreateTableStatement) x);
        }
        if (!printCommentAdvance) {
            printComment(x.getComment());
        }
        printPartitionBy(x);
        printRowFormat(x);
        printCreateTableLike(x);
        printStoredAs(x);
        printStoredBy(x);
        printLocation(x);
        printCached(x);
        printTableOptions(x);
        printLifeCycle(x.getLifeCycle());
        printSelectAs(x, printSelect);
    }

    protected void printUsing(HiveCreateTableStatement x) {
        SQLExpr using = x.getUsing();
        if (using != null) {
            println();
            print0(ucase ? "USING " : "using ");
            using.accept(this);
        }
    }

    protected void printSkewedBy(HiveCreateTableStatement x) {
        List<SQLExpr> skewedBy = x.getSkewedBy();
        if (!skewedBy.isEmpty()) {
            println();
            print0(ucase ? "SKEWED BY (" : "skewed by (");
            printAndAccept(skewedBy, ",");
            print(')');

            List<SQLExpr> skewedByOn = x.getSkewedByOn();
            if (!skewedByOn.isEmpty()) {
                print0(ucase ? " ON (" : " on (");
                printAndAccept(skewedByOn, ",");
                print(')');
            }
            if (x.isSkewedByStoreAsDirectories()) {
                print(ucase ? " STORED AS DIRECTORIES" : " stored as directories");
            }
        }
    }

    protected void printRowFormat(SQLCreateTableStatement x) {
        SQLExternalRecordFormat format = x.getRowFormat();
        SQLExpr storedBy = x.getStoredBy();
        if (format != null) {
            println();
            print0(ucase ? "ROW FORMAT" : "row format");
            if (format.getSerde() == null) {
                print0(ucase ? " DELIMITED" : " delimited ");
            }
            visit(format);
            if (storedBy == null) {
                if (x instanceof HiveCreateTableStatement) {
                    printSerdeProperties(((HiveCreateTableStatement) x).getSerdeProperties());
                }
            }
        }
    }

    protected void printStoredBy(SQLCreateTableStatement x) {
        SQLExpr storedBy = x.getStoredBy();
        if (storedBy != null) {
            printStoredBy(storedBy);

            if (x instanceof HiveCreateTableStatement) {
                Map<String, SQLObject> serdeProperties = ((HiveCreateTableStatement) x).getSerdeProperties();
                printSerdeProperties(serdeProperties);
            }
        }
    }

    protected void printStoredBy(SQLExpr storedBy) {
        if (storedBy == null) {
            return;
        }

        println();
        print0(ucase ? "STORED BY " : "stored by ");

        if (storedBy instanceof SQLIdentifierExpr) {
            String name = ((SQLIdentifierExpr) storedBy).getName();
            if (!name.isEmpty()
                    && name.charAt(0) != '`'
                    && name.charAt(0) != '"'
                    && name.charAt(0) != '\''
            ) {
                print('\'');
                print(name);
                print('\'');
                return;
            }
        }

        printExpr(storedBy, parameterized);
    }

    protected void printStoredAs(SQLCreateTableStatement x) {
        SQLExpr storedAs = x.getStoredAs();
        if (storedAs != null) {
            println();
            boolean isHiveCreateTable = x instanceof HiveCreateTableStatement;
            if (isHiveCreateTable && ((HiveCreateTableStatement) x).isLbracketUse()) {
                print("[");
            }
            print0(ucase ? "STORED AS" : "stored as");
            if (storedAs instanceof SQLIdentifierExpr) {
                print(' ');
                printExpr(storedAs, parameterized);
            } else {
                incrementIndent();
                println();
                printExpr(storedAs, parameterized);
                decrementIndent();
            }

            if (isHiveCreateTable && ((HiveCreateTableStatement) x).isRbracketUse()) {
                print("]");
            }
        }
    }

    protected void printSelectAs(SQLCreateTableStatement x, boolean printSelect) {
        SQLSelect select = x.getSelect();
        if (printSelect && select != null) {
            println();
            if (x instanceof HiveCreateTableStatement && ((HiveCreateTableStatement) x).isLikeQuery()) { // for dla
                print0(ucase ? "LIKE" : "like");
            } else {
                print0(ucase ? "AS" : "as");
            }
            println();
            visit(select);
        }
    }

    protected void printCached(SQLCreateTableStatement x) {
        // do nothing
    }

    protected void printTableElementsWithComment(SQLCreateTableStatement x) {
        final List<SQLTableElement> tableElementList = x.getTableElementList();
        int size = tableElementList.size();
        if (size > 0) {
            print0(" (");

            if (this.isPrettyFormat() && x.hasBodyBeforeComment()) {
                print(' ');
                printlnComment(x.getBodyBeforeCommentsDirect());
            }

            this.indentCount++;
            println();
            for (int i = 0; i < size; ++i) {
                SQLTableElement element = tableElementList.get(i);
                element.accept(this);

                if (i != size - 1) {
                    print(',');
                }
                if (this.isPrettyFormat() && element.hasAfterComment()) {
                    print(' ');
                    printlnComment(element.getAfterCommentsDirect());
                }

                if (i != size - 1) {
                    println();
                }
            }
            this.indentCount--;
            println();
            print(')');
        }
    }
}
