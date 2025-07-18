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
package com.alibaba.druid.sql.dialect.postgresql.visitor;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.ast.*;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.dialect.oracle.ast.OracleDataTypeIntervalDay;
import com.alibaba.druid.sql.dialect.oracle.ast.OracleDataTypeIntervalYear;
import com.alibaba.druid.sql.dialect.oracle.ast.clause.*;
import com.alibaba.druid.sql.dialect.oracle.ast.expr.*;
import com.alibaba.druid.sql.dialect.oracle.ast.stmt.*;
import com.alibaba.druid.sql.dialect.oracle.parser.OracleFunctionDataType;
import com.alibaba.druid.sql.dialect.oracle.parser.OracleProcedureDataType;
import com.alibaba.druid.sql.dialect.oracle.visitor.OracleASTVisitor;
import com.alibaba.druid.sql.dialect.postgresql.ast.expr.*;
import com.alibaba.druid.sql.dialect.postgresql.ast.stmt.*;
import com.alibaba.druid.sql.dialect.postgresql.ast.stmt.PGSelectQueryBlock.FetchClause;
import com.alibaba.druid.sql.dialect.postgresql.ast.stmt.PGSelectQueryBlock.ForClause;
import com.alibaba.druid.sql.visitor.ExportParameterVisitorUtils;
import com.alibaba.druid.sql.visitor.SQLASTOutputVisitor;
import com.alibaba.druid.util.FnvHash;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class PGOutputVisitor extends SQLASTOutputVisitor implements PGASTVisitor, OracleASTVisitor {
    public PGOutputVisitor(StringBuilder appender) {
        super(appender, DbType.postgresql);
    }
    public PGOutputVisitor(StringBuilder appender, DbType dbType) {
        super(appender, dbType);
    }

    public PGOutputVisitor(StringBuilder appender, boolean parameterized) {
        super(appender, DbType.postgresql, parameterized);
    }

    public PGOutputVisitor(StringBuilder appender, DbType dbType, boolean parameterized) {
        super(appender, DbType.postgresql, parameterized);
    }

    @Override
    public boolean visit(FetchClause x) {
        print0(ucase ? "FETCH " : "fetch ");
        if (FetchClause.Option.FIRST.equals(x.getOption())) {
            print0(ucase ? "FIRST " : "first ");
        } else if (FetchClause.Option.NEXT.equals(x.getOption())) {
            print0(ucase ? "NEXT " : "next ");
        }
        x.getCount().accept(this);
        print0(ucase ? " ROWS ONLY" : " rows only");
        return false;
    }

    @Override
    public boolean visit(ForClause x) {
        print0(ucase ? "FOR " : "for ");
        if (ForClause.Option.UPDATE.equals(x.getOption())) {
            print0(ucase ? "UPDATE" : "update");
        } else if (ForClause.Option.SHARE.equals(x.getOption())) {
            print0(ucase ? "SHARE" : "share");
        }

        if (x.getOf().size() > 0) {
            print(' ');
            for (int i = 0; i < x.getOf().size(); ++i) {
                if (i != 0) {
                    println(", ");
                }
                x.getOf().get(i).accept(this);
            }
        }

        if (x.isNoWait()) {
            print0(ucase ? " NOWAIT" : " nowait");
        } else if (x.isSkipLocked()) {
            print0(ucase ? " SKIP LOCKED" : " skip locked");
        }

        return false;
    }

    public boolean visit(PGSelectQueryBlock x) {
        if ((!isParameterized()) && isPrettyFormat() && x.hasBeforeComment()) {
            printlnComments(x.getBeforeCommentsDirect());
        }

        boolean bracket = x.isParenthesized();
        if (bracket) {
            if (x.getParent() instanceof SQLSelect && x.getParent().getParent() instanceof SQLSubqueryTableSource) {
                bracket = false;
            }
        }
        if (bracket) {
            print('(');
        }

        print0(ucase ? "SELECT " : "select ");

        if (SQLSetQuantifier.ALL == x.getDistionOption()) {
            print0(ucase ? "ALL " : "all ");
        } else if (SQLSetQuantifier.DISTINCT == x.getDistionOption()) {
            print0(ucase ? "DISTINCT " : "distinct ");

            List<SQLExpr> distinctOn = x.getDistinctOn();
            if (distinctOn != null && distinctOn.size() > 0) {
                print0(ucase ? "ON " : "on ");

                if (distinctOn.size() == 1 && distinctOn.get(0) instanceof SQLListExpr) {
                    printExpr(distinctOn.get(0));
                    print(' ');
                } else {
                    print0("(");
                    printAndAccept(distinctOn, ", ");
                    print0(") ");
                }
            }
        }

        printSelectList(x.getSelectList());

        if (x.getInto() != null) {
            println();
            if (x.getIntoOption() != null) {
                print0(x.getIntoOption().name());
                print(' ');
            }

            print0(ucase ? "INTO " : "into ");
            x.getInto().accept(this);
        }

        printFrom(x);
        printWhere(x);
        printGroupBy(x);
        printWindow(x);
        printOrderBy(x);
        printLimit(x);

        if (x.getFetch() != null) {
            println();
            x.getFetch().accept(this);
        }

        if (x.getForClause() != null) {
            println();
            x.getForClause().accept(this);
        }

        if (bracket) {
            print(')');
        }

        return false;
    }

    @Override
    public boolean visit(SQLTruncateStatement x) {
        print0(ucase ? "TRUNCATE TABLE " : "truncate table ");
        if (x.isOnly()) {
            print0(ucase ? "ONLY " : "only ");
        }

        printlnAndAccept(x.getTableSources(), ",");

        if (x.getRestartIdentity() != null) {
            if (x.getRestartIdentity().booleanValue()) {
                print0(ucase ? " RESTART IDENTITY" : " restart identity");
            } else {
                print0(ucase ? " CONTINUE IDENTITY" : " continue identity");
            }
        }

        if (x.getCascade() != null) {
            if (x.getCascade().booleanValue()) {
                print0(ucase ? " CASCADE" : " cascade");
            } else {
                print0(ucase ? " RESTRICT" : " restrict");
            }
        }
        return false;
    }

    @Override
    public boolean visit(PGDeleteStatement x) {
        if (x.getWith() != null) {
            x.getWith().accept(this);
            println();
        }

        print0(ucase ? "DELETE FROM " : "delete from ");

        if (x.isOnly()) {
            print0(ucase ? "ONLY " : "only ");
        }

        printTableSourceExpr(x.getTableName());

        if (x.getAlias() != null) {
            print0(ucase ? " AS " : " as ");
            print0(x.getAlias());
        }

        SQLTableSource using = x.getUsing();
        if (using != null) {
            println();
            print0(ucase ? "USING " : "using ");
            using.accept(this);
        }

        if (x.getWhere() != null) {
            println();
            print0(ucase ? "WHERE " : "where ");
            this.indentCount++;
            x.getWhere().accept(this);
            this.indentCount--;
        }

        if (x.isReturning()) {
            println();
            print0(ucase ? "RETURNING *" : "returning *");
        }

        return false;
    }

    @Override
    public boolean visit(PGInsertStatement x) {
        if (x.getWith() != null) {
            x.getWith().accept(this);
            println();
        }

        print0(ucase ? "INSERT INTO " : "insert into ");

        x.getTableSource().accept(this);

        printInsertColumns(x.getColumns());

        printValuesOrQuery(x);

        printOnConflict(x);

        printReturning(x);
        return false;
    }

    protected void printReturning(PGInsertStatement x) {
        if (x.getReturning() != null) {
            println();
            print0(ucase ? "RETURNING " : "returning ");
            x.getReturning().accept(this);
        }
    }

    protected void printValuesOrQuery(PGInsertStatement x) {
        if (x.getValues() != null) {
            println();
            print0(ucase ? "VALUES " : "values ");
            printlnAndAccept(x.getValuesList(), ",");
        } else {
            if (x.getQuery() != null) {
                println();
                x.getQuery().accept(this);
            }
        }
    }

    protected void printOnConflict(PGInsertStatement x) {
        List<SQLExpr> onConflictTarget = x.getOnConflictTarget();
        List<SQLUpdateSetItem> onConflictUpdateSetItems = x.getOnConflictUpdateSetItems();
        boolean onConflictDoNothing = x.isOnConflictDoNothing();

        if (onConflictDoNothing
                || (onConflictTarget != null && onConflictTarget.size() > 0)
                || (onConflictUpdateSetItems != null && onConflictUpdateSetItems.size() > 0)) {
            println();
            print0(ucase ? "ON CONFLICT" : "on conflict");

            if ((onConflictTarget != null && onConflictTarget.size() > 0)) {
                print0(" (");
                printAndAccept(onConflictTarget, ", ");
                print(')');
            }

            SQLName onConflictConstraint = x.getOnConflictConstraint();
            if (onConflictConstraint != null) {
                print0(ucase ? " ON CONSTRAINT " : " on constraint ");
                printExpr(onConflictConstraint);
            }

            SQLExpr onConflictWhere = x.getOnConflictWhere();
            if (onConflictWhere != null) {
                print0(ucase ? " WHERE " : " where ");
                printExpr(onConflictWhere);
            }

            if (onConflictDoNothing) {
                print0(ucase ? " DO NOTHING" : " do nothing");
            } else if ((onConflictUpdateSetItems != null && onConflictUpdateSetItems.size() > 0)) {
                print0(ucase ? " DO UPDATE SET " : " do update set ");
                printAndAccept(onConflictUpdateSetItems, ", ");
                SQLExpr onConflictUpdateWhere = x.getOnConflictUpdateWhere();
                if (onConflictUpdateWhere != null) {
                    print0(ucase ? " WHERE " : " where ");
                    printExpr(onConflictUpdateWhere);
                }
            }
        }
    }

    @Override
    public boolean visit(PGSelectStatement x) {
        return visit((SQLSelectStatement) x);
    }

    @Override
    public boolean visit(PGUpdateStatement x) {
        SQLWithSubqueryClause with = x.getWith();
        if (with != null) {
            visit(with);
            println();
        }

        print0(ucase ? "UPDATE " : "update ");

        if (x.isOnly()) {
            print0(ucase ? "ONLY " : "only ");
        }

        printTableSource(x.getTableSource());

        println();
        print0(ucase ? "SET " : "set ");
        for (int i = 0, size = x.getItems().size(); i < size; ++i) {
            if (i != 0) {
                print0(", ");
            }
            SQLUpdateSetItem item = x.getItems().get(i);
            visit(item);
        }

        SQLTableSource from = x.getFrom();
        if (from != null) {
            println();
            print0(ucase ? "FROM " : "from ");
            printTableSource(from);
        }

        SQLExpr where = x.getWhere();
        if (where != null) {
            println();
            indentCount++;
            print0(ucase ? "WHERE " : "where ");
            printExpr(where);
            indentCount--;
        }

        List<SQLExpr> returning = x.getReturning();
        if (returning.size() > 0) {
            println();
            print0(ucase ? "RETURNING " : "returning ");
            printAndAccept(returning, ", ");
        }

        return false;
    }

    @Override
    public boolean visit(PGFunctionTableSource x) {
        x.getExpr().accept(this);

        String alias = x.getAlias();
        List<SQLParameter> parameters = x.getParameters();
        if (alias != null || !x.getParameters().isEmpty()) {
            print0(ucase ? " AS" : " as");
        }

        if (alias != null) {
            print(' ');
            print0(alias);
        }

        if (!parameters.isEmpty()) {
            incrementIndent();
            println();
            print('(');
            printAndAccept(parameters, ", ");
            print(')');
            decrementIndent();
        }

        return false;
    }

    @Override
    public boolean visit(PGTypeCastExpr x) {
        SQLExpr expr = x.getExpr();
        SQLDataType dataType = x.getDataType();

        if (dataType.nameHashCode64() == FnvHash.Constants.VARBIT) {
            dataType.accept(this);
            print(' ');
            printExpr(expr);
            return false;
        }

        if (expr != null) {
            if (expr instanceof SQLBinaryOpExpr) {
                expr.accept(this);
            } else if (expr instanceof PGTypeCastExpr && dataType.getArguments().isEmpty()) {
                dataType.accept(this);
                print('(');
                visit((PGTypeCastExpr) expr);
                print(')');
                return false;
            } else {
                expr.accept(this);
            }
        }
        print0("::");
        dataType.accept(this);
        return false;
    }

    @Override
    public boolean visit(PGExtractExpr x) {
        print0(ucase ? "EXTRACT(" : "extract(");
        print0(x.getField().name());
        print0(ucase ? " FROM " : " from ");
        x.getSource().accept(this);
        print(')');
        return false;
    }

    @Override
    public boolean visit(PGAttrExpr x) {
        int curLen = this.appender.length();
        if (curLen > 0) {
            char c = this.appender.charAt(curLen - 1);
            if (!(c == ' ' || c == '\t' || c == '\n' || c == '\r')) {
                this.appender.append(" ");
            }
        }

        x.getName().accept(this);
        if (x.getMode() == PGAttrExpr.PGExprMode.EQ) {
            print0(" = ");
        } else {
            print0(" ");
        }

        x.getValue().accept(this);
        return false;
    }

    @Override
    public boolean visit(PGBoxExpr x) {
        print0(ucase ? "BOX " : "box ");
        x.getValue().accept(this);
        return false;
    }

    @Override
    public boolean visit(PGPointExpr x) {
        print0(ucase ? "POINT " : "point ");
        x.getValue().accept(this);
        return false;
    }

    @Override
    public boolean visit(PGMacAddrExpr x) {
        print0("macaddr ");
        x.getValue().accept(this);
        return false;
    }

    @Override
    public boolean visit(PGInetExpr x) {
        print0("inet ");
        x.getValue().accept(this);
        return false;
    }

    @Override
    public boolean visit(PGCidrExpr x) {
        print0("cidr ");
        x.getValue().accept(this);
        return false;
    }

    @Override
    public boolean visit(PGPolygonExpr x) {
        print0("polygon ");
        x.getValue().accept(this);
        return false;
    }

    @Override
    public boolean visit(PGCircleExpr x) {
        print0("circle ");
        x.getValue().accept(this);
        return false;
    }

    @Override
    public boolean visit(PGLineSegmentsExpr x) {
        print0("lseg ");
        x.getValue().accept(this);
        return false;
    }

    @Override
    public boolean visit(SQLBinaryExpr x) {
        print0(ucase ? "B'" : "b'");
        print0(x.getText());
        print('\'');

        return false;
    }

    @Override
    public boolean visit(PGShowStatement x) {
        print0(ucase ? "SHOW " : "show ");
        x.getExpr().accept(this);
        return false;
    }

    public boolean visit(SQLLimit x) {
        if (x.getRowCount() != null) {
            print0(ucase ? "LIMIT " : "limit ");
            x.getRowCount().accept(this);
        }
        if (x.getOffset() != null) {
            if (x.getRowCount() != null) {
                print0(ucase ? " OFFSET " : " offset ");
            } else {
                print0(ucase ? "OFFSET " : "offset ");
            }
            x.getOffset().accept(this);
        }
        return false;
    }

    @Override
    public boolean visit(PGStartTransactionStatement x) {
        if (x.isUseBegin()) {
            print0(ucase ? "BEGIN" : "begin");
            return false;
        }
        print0(ucase ? "START TRANSACTION" : "start transaction");
        return false;
    }

    @Override
    public boolean visit(PGDoStatement x) {
        print0(ucase ? "DO " : "do ");
        x.getBlock().accept(this);
        return false;
    }

    @Override
    public boolean visit(SQLBlockStatement x) {
        if (x.isDollarQuoted()) {
            print(x.getDollarQuoteTagName() == null ? "$$" : "$" + x.getDollarQuoteTagName() + "$");
            println();
        }

        if (x.getLabelName() != null) {
            print0(x.getLabelName());
            println();
        }

        if (!x.getParameters().isEmpty()) {
            this.indentCount++;
            if (x.getParent() instanceof SQLCreateProcedureStatement) {
                SQLCreateProcedureStatement procedureStatement = (SQLCreateProcedureStatement) x.getParent();
                if (procedureStatement.isCreate()) {
                    printIndent();
                }
            }
            if (!(x.getParent() instanceof SQLCreateProcedureStatement
                    || x.getParent() instanceof SQLCreateFunctionStatement
                    || x.getParent() instanceof OracleFunctionDataType
                    || x.getParent() instanceof OracleProcedureDataType)
            ) {
                print0(ucase ? "DECLARE" : "declare");
                println();
            }

            for (int i = 0, size = x.getParameters().size(); i < size; ++i) {
                if (i != 0) {
                    println();
                }
                SQLParameter param = x.getParameters().get(i);
                param.accept(this);
                print(';');
            }

            this.indentCount--;
            println();
        }
        if (x.isHaveBeginEnd()) {
            print0(ucase ? "BEGIN" : "begin");
        }
        this.indentCount++;

        for (int i = 0, size = x.getStatementList().size(); i < size; ++i) {
            println();
            SQLStatement stmt = x.getStatementList().get(i);
            stmt.accept(this);
        }
        this.indentCount--;

        SQLStatement exception = x.getException();
        if (exception != null) {
            println();
            exception.accept(this);
        }

        println();
        if (x.isHaveBeginEnd()) {
            // END [label];
            if (x.getEndLabel() != null) {
                print0(ucase ? "END " : "end ");
                print0(x.getEndLabel());
                print(';');
            } else {
                print0(ucase ? "END;" : "end;");
            }

            if (x.isDollarQuoted()) {
                println();
                print(x.getDollarQuoteTagName() == null ? "$$" : "$" + x.getDollarQuoteTagName() + "$");
                if (x.getLanguage() != null) {
                    print0(" LANGUAGE " + x.getLanguage());
                }
                print(';');
            }
        }
        return false;
    }

    @Override
    public boolean visit(PGEndTransactionStatement x) {
        print0(ucase ? "END" : "end");
        return false;
    }

    @Override
    public boolean visit(PGConnectToStatement x) {
        print0(ucase ? "CONNECT TO " : "connect to ");
        x.getTarget().accept(this);
        return false;
    }

    @Override
    public boolean visit(PGCreateDatabaseStatement x) {
        printUcase("CREATE DATABASE ");

        if (x.getName() != null) {
            x.getName().accept(this);
        }

        if (x.isHaveWith()) {
            printUcase(" WITH");
        }

        for (PGAttrExpr attrExpr : x.getStats()) {
            attrExpr.accept(this);
        }

        return false;
    }

    @Override
    public boolean visit(PGDropDatabaseStatement x) {
        print0(ucase ? "DROP DATABASE " : "drop database ");

        if (x.isIfExists()) {
            print0(ucase ? "IF EXISTS " : "if exists ");
        }

        x.getName().accept(this);

        if (x.isForce()) {
            if (x.isUsingWith()) {
                print0(ucase ? " WITH" : " with");
            }
            print0(ucase ? " FORCE" : " force");
        }

        return false;
    }

    @Override
    public boolean visit(PGCreateSchemaStatement x) {
        printUcase("CREATE SCHEMA ");
        if (x.isIfNotExists()) {
            printUcase("IF NOT EXISTS ");
        }

        if (x.getSchemaName() != null) {
            x.getSchemaName().accept(this);
        }
        if (x.isAuthorization()) {
            printUcase("AUTHORIZATION ");
            x.getUserName().accept(this);
        }

        return false;
    }

    @Override
    public boolean visit(PGDropSchemaStatement x) {
        printUcase("DROP SCHEMA ");
        if (x.isIfExists()) {
            printUcase("IF EXISTS ");
        }

        List<SQLName> multipleName = x.getMultipleNames();
        for (int i = 0; i < multipleName.size(); i++) {
            if (i > 0) {
                printUcase(", ");
            }
            multipleName.get(i).accept(this);
        }

        if (x.isCascade()) {
            print0(ucase ? " CASCADE" : " cascade");
        }
        if (x.isRestrict()) {
            print0(ucase ? " RESTRICT" : " restrict");
        }

        return false;
    }

    @Override
    public boolean visit(PGAlterSchemaStatement x) {
        printUcase("ALTER SCHEMA ");
        x.getSchemaName().accept(this);

        if (x.getNewName() != null) {
            print0(" RENAME TO ");
            x.getNewName().accept(this);
        } else if (x.getNewOwner() != null) {
            print0(" OWNER TO ");
            x.getNewOwner().accept(this);
        }
        return false;
    }

    @Override
    public boolean visit(SQLSetStatement x) {
        if (x.isUseSet()) {
            print0(ucase ? "SET " : "set ");
        }

        SQLSetStatement.Option option = x.getOption();
        if (option != null) {
            print(option.name());
            print(' ');
        }

        printAndAccept(x.getItems(), ", ");

        return false;
    }

    @Override
    public boolean visit(SQLAssignItem x) {
        if (!(x.getParent() instanceof SQLSetStatement)) {
            return super.visit(x);
        }

        x.getTarget().accept(this);
        SQLExpr value = x.getValue();
        boolean needSpace = false;
        if (x.getTarget() instanceof SQLIdentifierExpr) {
            String name = ((SQLIdentifierExpr) x.getTarget()).getName();
            needSpace = "TIME ZONE".equalsIgnoreCase(name) || "schema".equalsIgnoreCase(name) || "names".equalsIgnoreCase(name);
        }
        if (needSpace) {
            print(' ');
        } else {
            if (!((SQLSetStatement) x.getParent()).isUseSet()) {
                print0(" := ");
            } else if (value instanceof SQLPropertyExpr
                && ((SQLPropertyExpr) value).getOwner() instanceof SQLVariantRefExpr) {
                print0(" := ");
            } else {
                print0(" TO ");
            }
        }

        if (value instanceof SQLListExpr) {
            SQLListExpr listExpr = (SQLListExpr) value;
            printAndAccept(listExpr.getItems(), ", ");
        } else {
            value.accept(this);
        }

        return false;
    }

    @Override
    public boolean visit(SQLCreateUserStatement x) {
        print0(ucase ? "CREATE USER " : "create user ");
        x.getUser().accept(this);
        if (x.isPostgresqlWith()) {
            print0(ucase ? " WITH " : " with ");
        }
        if (x.isPostgresqlEncrypted()) {
            print0(ucase ? " ENCRYPTED " : " encrypted ");
        }
        print0(ucase ? " PASSWORD " : " password ");

        SQLExpr password = x.getPassword();

        if (password instanceof SQLIdentifierExpr) {
            print('\'');
            password.accept(this);
            print('\'');
        } else {
            password.accept(this);
        }
        return false;
    }

    protected void printGrantPrivileges(SQLGrantStatement x) {
        List<SQLPrivilegeItem> privileges = x.getPrivileges();
        int i = 0;
        for (SQLPrivilegeItem privilege : privileges) {
            if (i != 0) {
                print(", ");
            }

            SQLExpr action = privilege.getAction();
            if (action instanceof SQLIdentifierExpr) {
                String name = ((SQLIdentifierExpr) action).getName();
                if ("RESOURCE".equalsIgnoreCase(name)) {
                    continue;
                }
            }

            privilege.accept(this);
            i++;
        }
    }

    public boolean visit(SQLGrantStatement x) {
        if (x.getResource() == null) {
            print("ALTER ROLE ");

            printAndAccept(x.getUsers(), ",");

            print(' ');
            Set<SQLIdentifierExpr> pgPrivilegs = new LinkedHashSet<SQLIdentifierExpr>();
            for (SQLPrivilegeItem privilege : x.getPrivileges()) {
                SQLExpr action = privilege.getAction();
                if (action instanceof SQLIdentifierExpr) {
                    String name = ((SQLIdentifierExpr) action).getName();
                    if (name.equalsIgnoreCase("CONNECT")) {
                        pgPrivilegs.add(new SQLIdentifierExpr("LOGIN"));
                    }
                    if (name.toLowerCase().startsWith("create ")) {
                        pgPrivilegs.add(new SQLIdentifierExpr("CREATEDB"));
                    }
                }
            }
            int i = 0;
            for (SQLExpr privilege : pgPrivilegs) {
                if (i != 0) {
                    print(' ');
                }
                privilege.accept(this);
                i++;
            }
            return false;
        }

        return super.visit(x);
    }
    /** **************************************************************************/
    // for oracle to postsql

    /**
     *
     **************************************************************************/

    public boolean visit(OracleSysdateExpr x) {
        print0(ucase ? "CURRENT_TIMESTAMP" : "CURRENT_TIMESTAMP");
        return false;
    }

    @Override
    public boolean visit(SQLExceptionStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLExceptionStatement.Item x) {
        return false;
    }

    @Override
    public boolean visit(OracleArgumentExpr x) {
        return false;
    }

    @Override
    public boolean visit(OracleSetTransactionStatement x) {
        return false;
    }

    @Override
    public boolean visit(OracleExplainStatement x) {
        return false;
    }

    @Override
    public boolean visit(OracleAlterTableDropPartition x) {
        return false;
    }

    @Override
    public boolean visit(OracleAlterTableTruncatePartition x) {
        return false;
    }

    @Override
    public boolean visit(OracleAlterTableSplitPartition.TableSpaceItem x) {
        return false;
    }

    @Override
    public boolean visit(OracleAlterTableSplitPartition.UpdateIndexesClause x) {
        return false;
    }

    @Override
    public boolean visit(OracleAlterTableSplitPartition.NestedTablePartitionSpec x) {
        return false;
    }

    @Override
    public boolean visit(OracleAlterTableSplitPartition x) {
        return false;
    }

    @Override
    public boolean visit(OracleAlterTableModify x) {
        return false;
    }

    @Override
    public boolean visit(OracleCreateIndexStatement x) {
        return false;
    }

    @Override
    public boolean visit(OracleForStatement x) {
        return false;
    }

    @Override
    public boolean visit(OracleFileSpecification x) {
        return false;
    }

    @Override
    public boolean visit(OracleAlterTablespaceAddDataFile x) {
        return false;
    }

    @Override
    public boolean visit(OracleAlterTablespaceStatement x) {
        return false;
    }

    @Override
    public boolean visit(OracleExitStatement x) {
        return false;
    }

    @Override
    public boolean visit(OracleContinueStatement x) {
        return false;
    }

    @Override
    public boolean visit(OracleRaiseStatement x) {
        return false;
    }

    @Override
    public boolean visit(OracleCreateDatabaseDbLinkStatement x) {
        return false;
    }

    @Override
    public boolean visit(OracleDropDbLinkStatement x) {
        return false;
    }

    @Override
    public boolean visit(OracleDataTypeIntervalYear x) {
        return false;
    }

    @Override
    public boolean visit(OracleDataTypeIntervalDay x) {
        return false;
    }

    @Override
    public boolean visit(OracleUsingIndexClause x) {
        return false;
    }

    @Override
    public boolean visit(OracleLobStorageClause x) {
        return false;
    }

    public boolean visit(OracleSelectTableReference x) {
        if (x.isOnly()) {
            print0(ucase ? "ONLY (" : "only (");
            printTableSourceExpr(x.getExpr());

            if (x.getPartition() != null) {
                print(' ');
                x.getPartition().accept(this);
            }

            print(')');
        } else {
            printTableSourceExpr(x.getExpr());

            if (x.getPartition() != null) {
                print(' ');
                x.getPartition().accept(this);
            }
        }

        if (x.getHints().size() > 0) {
            this.printHints(x.getHints());
        }

        if (x.getSampleClause() != null) {
            print(' ');
            x.getSampleClause().accept(this);
        }

        if (x.getPivot() != null) {
            println();
            x.getPivot().accept(this);
        }

        SQLUnpivot unpivot = x.getUnpivot();
        if (unpivot != null) {
            println();
            unpivot.accept(this);
        }

        printAlias(x.getAlias());

        return false;
    }

    @Override
    public boolean visit(PartitionExtensionClause x) {
        return false;
    }

    private void printHints(List<SQLHint> hints) {
        if (hints.size() > 0) {
            print0("/*+ ");
            printAndAccept(hints, ", ");
            print0(" */");
        }
    }

    public boolean visit(OracleIntervalExpr x) {
        if (x.getValue() instanceof SQLLiteralExpr || x.getValue() instanceof SQLVariantRefExpr) {
            print0(ucase ? "INTERVAL " : "interval ");
        }
        x.getValue().accept(this);
        print(' ');

        print0(x.getType().name());

        if (x.getPrecision() != null) {
            print('(');
            printExpr(x.getPrecision());
            if (x.getFactionalSecondsPrecision() != null) {
                print0(", ");
                print(x.getFactionalSecondsPrecision().intValue());
            }
            print(')');
        }

        if (x.getToType() != null) {
            print0(ucase ? " TO " : " to ");
            print0(x.getToType().name());
            if (x.getToFactionalSecondsPrecision() != null) {
                print('(');
                printExpr(x.getToFactionalSecondsPrecision());
                print(')');
            }
        }

        return false;
    }

    @Override
    public boolean visit(OracleOuterExpr x) {
        x.getExpr().accept(this);
        print0("(+)");
        return false;
    }

    public boolean visit(OracleBinaryFloatExpr x) {
        if (x != null && x.getValue() != null) {
            print0(x.getValue().toString());
            print('F');
        }
        return false;
    }

    public boolean visit(OracleBinaryDoubleExpr x) {
        if (x != null && x.getValue() != null) {
            print0(x.getValue().toString());
            print('D');
        }
        return false;
    }

    @Override
    public boolean visit(OracleIsSetExpr x) {
        x.getNestedTable().accept(this);
        print0(ucase ? " IS A SET" : " is a set");
        return false;
    }

    @Override
    public boolean visit(ModelClause.ReturnRowsClause x) {
        if (x.isAll()) {
            print0(ucase ? "RETURN ALL ROWS" : "return all rows");
        } else {
            print0(ucase ? "RETURN UPDATED ROWS" : "return updated rows");
        }
        return false;
    }

    @Override
    public boolean visit(ModelClause.MainModelClause x) {
        if (x.getMainModelName() != null) {
            print0(ucase ? " MAIN " : " main ");
            x.getMainModelName().accept(this);
        }

        println();
        x.getModelColumnClause().accept(this);

        for (ModelClause.CellReferenceOption opt : x.getCellReferenceOptions()) {
            println();
            print0(opt.name);
        }

        println();
        x.getModelRulesClause().accept(this);

        return false;
    }

    @Override
    public boolean visit(ModelClause.ModelColumnClause x) {
        if (x.getQueryPartitionClause() != null) {
            x.getQueryPartitionClause().accept(this);
            println();
        }

        print0(ucase ? "DIMENSION BY (" : "dimension by (");
        printAndAccept(x.getDimensionByColumns(), ", ");
        print(')');

        println();
        print0(ucase ? "MEASURES (" : "measures (");
        printAndAccept(x.getMeasuresColumns(), ", ");
        print(')');
        return false;
    }

    @Override
    public boolean visit(ModelClause.QueryPartitionClause x) {
        print0(ucase ? "PARTITION BY (" : "partition by (");
        printAndAccept(x.getExprList(), ", ");
        print(')');
        return false;
    }

    @Override
    public boolean visit(ModelClause.ModelColumn x) {
        x.getExpr().accept(this);
        if (x.getAlias() != null) {
            print(' ');
            print0(x.getAlias());
        }
        return false;
    }

    @Override
    public boolean visit(ModelClause.ModelRulesClause x) {
        if (x.getOptions().size() > 0) {
            print0(ucase ? "RULES" : "rules");
            for (ModelClause.ModelRuleOption opt : x.getOptions()) {
                print(' ');
                print0(opt.name);
            }
        }

        if (x.getIterate() != null) {
            print0(ucase ? " ITERATE (" : " iterate (");
            x.getIterate().accept(this);
            print(')');

            if (x.getUntil() != null) {
                print0(ucase ? " UNTIL (" : " until (");
                x.getUntil().accept(this);
                print(')');
            }
        }

        print0(" (");
        printAndAccept(x.getCellAssignmentItems(), ", ");
        print(')');
        return false;

    }

    @Override
    public boolean visit(ModelClause.CellAssignmentItem x) {
        if (x.getOption() != null) {
            print0(x.getOption().name);
            print(' ');
        }

        x.getCellAssignment().accept(this);

        if (x.getOrderBy() != null) {
            print(' ');
            x.getOrderBy().accept(this);
        }

        print0(" = ");
        x.getExpr().accept(this);

        return false;
    }

    @Override
    public boolean visit(ModelClause.CellAssignment x) {
        x.getMeasureColumn().accept(this);
        print0("[");
        printAndAccept(x.getConditions(), ", ");
        print0("]");
        return false;
    }

    @Override
    public boolean visit(ModelClause x) {
        print0(ucase ? "MODEL" : "model");

        this.indentCount++;
        for (ModelClause.CellReferenceOption opt : x.getCellReferenceOptions()) {
            print(' ');
            print0(opt.name);
        }

        if (x.getReturnRowsClause() != null) {
            print(' ');
            x.getReturnRowsClause().accept(this);
        }

        for (ModelClause.ReferenceModelClause item : x.getReferenceModelClauses()) {
            print(' ');
            item.accept(this);
        }

        x.getMainModel().accept(this);
        this.indentCount--;

        return false;
    }

    @Override
    public boolean visit(OracleReturningClause x) {
        print0(ucase ? "RETURNING " : "returning ");
        printAndAccept(x.getItems(), ", ");
        print0(ucase ? " INTO " : " into ");
        printAndAccept(x.getValues(), ", ");

        return false;
    }

    @Override
    public boolean visit(OracleInsertStatement x) {
        //visit((SQLInsertStatement) x);

        print0(ucase ? "INSERT " : "insert ");

        if (x.getHints().size() > 0) {
            printAndAccept(x.getHints(), ", ");
            print(' ');
        }

        print0(ucase ? "INTO " : "into ");

        x.getTableSource().accept(this);

        printInsertColumns(x.getColumns());

        if (x.getValues() != null) {
            println();
            print0(ucase ? "VALUES " : "values ");
            x.getValues().accept(this);
        } else {
            if (x.getQuery() != null) {
                println();
                x.getQuery().accept(this);
            }
        }

        if (x.getReturning() != null) {
            println();
            x.getReturning().accept(this);
        }

        if (x.getErrorLogging() != null) {
            println();
            x.getErrorLogging().accept(this);
        }

        return false;
    }

    @Override
    public boolean visit(OracleMultiInsertStatement.InsertIntoClause x) {
        print0(ucase ? "INTO " : "into ");

        x.getTableSource().accept(this);

        if (x.getColumns().size() > 0) {
            this.indentCount++;
            println();
            print('(');
            for (int i = 0, size = x.getColumns().size(); i < size; ++i) {
                if (i != 0) {
                    if (i % 5 == 0) {
                        println();
                    }
                    print0(", ");
                }
                x.getColumns().get(i).accept(this);
            }
            print(')');
            this.indentCount--;
        }

        if (x.getValues() != null) {
            println();
            print0(ucase ? "VALUES " : "values ");
            x.getValues().accept(this);
        } else {
            if (x.getQuery() != null) {
                println();
                x.getQuery().accept(this);
            }
        }

        return false;
    }

    @Override
    public boolean visit(OracleMultiInsertStatement x) {
        print0(ucase ? "INSERT " : "insert ");

        if (x.getHints().size() > 0) {
            this.printHints(x.getHints());
        }

        if (x.getOption() != null) {
            print0(x.getOption().name());
            print(' ');
        }

        for (int i = 0, size = x.getEntries().size(); i < size; ++i) {
            this.indentCount++;
            println();
            x.getEntries().get(i).accept(this);
            this.indentCount--;
        }

        println();
        x.getSubQuery().accept(this);

        return false;
    }

    @Override
    public boolean visit(OracleMultiInsertStatement.ConditionalInsertClause x) {
        for (int i = 0, size = x.getItems().size(); i < size; ++i) {
            if (i != 0) {
                println();
            }

            OracleMultiInsertStatement.ConditionalInsertClauseItem item = x.getItems().get(i);

            item.accept(this);
        }

        if (x.getElseItem() != null) {
            println();
            print0(ucase ? "ELSE" : "else");
            this.indentCount++;
            println();
            x.getElseItem().accept(this);
            this.indentCount--;
        }

        return false;
    }

    @Override
    public boolean visit(OracleMultiInsertStatement.ConditionalInsertClauseItem x) {
        print0(ucase ? "WHEN " : "when ");
        x.getWhen().accept(this);
        print0(ucase ? " THEN" : " then");
        this.indentCount++;
        println();
        x.getThen().accept(this);
        this.indentCount--;
        return false;
    }

    @Override
    public boolean visit(OracleSelectQueryBlock x) {
        if (isPrettyFormat() && x.hasBeforeComment()) {
            printlnComments(x.getBeforeCommentsDirect());
        }

        print0(ucase ? "SELECT " : "select ");

        if (x.getHintsSize() > 0) {
            printAndAccept(x.getHints(), ", ");
            print(' ');
        }

        if (SQLSetQuantifier.ALL == x.getDistionOption()) {
            print0(ucase ? "ALL " : "all ");
        } else if (SQLSetQuantifier.DISTINCT == x.getDistionOption()) {
            print0(ucase ? "DISTINCT " : "distinct ");
        } else if (SQLSetQuantifier.UNIQUE == x.getDistionOption()) {
            print0(ucase ? "UNIQUE " : "unique ");
        }

        printSelectList(x.getSelectList());

        if (x.getInto() != null) {
            println();
            print0(ucase ? "INTO " : "into ");
            x.getInto().accept(this);
        }

        println();
        print0(ucase ? "FROM " : "from ");
        if (x.getFrom() != null) {
            if (x.getCommentsAfterFrom() != null) {
                printAfterComments(x.getCommentsAfterFrom());
                println();
            }
            x.getFrom().accept(this);
        }

        if (x.getWhere() != null) {
            println();
            print0(ucase ? "WHERE " : "where ");
            x.getWhere().accept(this);
        }

        printHierarchical(x);

        if (x.getGroupBy() != null) {
            println();
            x.getGroupBy().accept(this);
        }

        if (x.getModelClause() != null) {
            println();
            x.getModelClause().accept(this);
        }

        SQLOrderBy orderBy = x.getOrderBy();
        if (orderBy != null) {
            println();
            orderBy.accept(this);
        }

        printFetchFirst(x);

        if (x.isForUpdate()) {
            println();
            print0(ucase ? "FOR UPDATE" : "for update");
            if (x.getForUpdateOfSize() > 0) {
                print(" OF ");
                printAndAccept(x.getForUpdateOf(), ", ");
            }

            if (x.isNoWait()) {
                print0(ucase ? " NOWAIT" : " nowait");
            } else if (x.isSkipLocked()) {
                print0(ucase ? " SKIP LOCKED" : " skip locked");
            } else if (x.getWaitTime() != null) {
                print0(ucase ? " WAIT " : " wait ");
                x.getWaitTime().accept(this);
            }
        }

        return false;
    }

    @Override
    public boolean visit(OracleLockTableStatement x) {
        print0(ucase ? "LOCK TABLE " : "lock table ");
        x.getTable().accept(this);
        print0(ucase ? " IN " : " in ");
        print0(x.getLockMode().toString());
        print0(ucase ? " MODE " : " mode ");
        if (x.isNoWait()) {
            print0(ucase ? "NOWAIT" : "nowait");
        } else if (x.getWait() != null) {
            print0(ucase ? "WAIT " : "wait ");
            x.getWait().accept(this);
        }
        return false;
    }

    @Override
    public boolean visit(OracleAlterSessionStatement x) {
        print0(ucase ? "ALTER SESSION SET " : "alter session set ");
        printAndAccept(x.getItems(), ", ");
        return false;
    }

    public boolean visit(OracleRangeExpr x) {
        x.getLowBound().accept(this);
        print0("..");
        x.getUpBound().accept(this);
        return false;
    }

    public boolean visit(OracleCheck x) {
        visit((SQLCheck) x);
        return false;
    }

    @Override
    public boolean visit(OracleSupplementalIdKey x) {
        print0(ucase ? "SUPPLEMENTAL LOG DATA (" : "supplemental log data (");

        int count = 0;

        if (x.isAll()) {
            print0(ucase ? "ALL" : "all");
            count++;
        }

        if (x.isPrimaryKey()) {
            if (count != 0) {
                print0(", ");
            }
            print0(ucase ? "PRIMARY KEY" : "primary key");
            count++;
        }

        if (x.isUnique()) {
            if (count != 0) {
                print0(", ");
            }
            print0(ucase ? "UNIQUE" : "unique");
            count++;
        }

        if (x.isUniqueIndex()) {
            if (count != 0) {
                print0(", ");
            }
            print0(ucase ? "UNIQUE INDEX" : "unique index");
            count++;
        }

        if (x.isForeignKey()) {
            if (count != 0) {
                print0(", ");
            }
            print0(ucase ? "FOREIGN KEY" : "foreign key");
            count++;
        }

        print0(ucase ? ") COLUMNS" : ") columns");
        return false;
    }

    @Override
    public boolean visit(OracleSupplementalLogGrp x) {
        print0(ucase ? "SUPPLEMENTAL LOG GROUP " : "supplemental log group ");
        x.getGroup().accept(this);
        print0(" (");
        printAndAccept(x.getColumns(), ", ");
        print(')');
        if (x.isAlways()) {
            print0(ucase ? " ALWAYS" : " always");
        }
        return false;
    }

    @Override
    public boolean visit(OracleCreateTableStatement.Organization x) {
        String type = x.getType();

        print0(ucase ? "ORGANIZATION " : "organization ");
        print0(ucase ? type : type.toLowerCase());

        printOracleSegmentAttributes(x);

        if (x.getPctthreshold() != null) {
            println();
            print0(ucase ? "PCTTHRESHOLD " : "pctthreshold ");
            print(x.getPctfree());
        }

        if ("EXTERNAL".equalsIgnoreCase(type)) {
            print0(" (");

            this.indentCount++;
            if (x.getExternalType() != null) {
                println();
                print0(ucase ? "TYPE " : "type ");
                x.getExternalType().accept(this);
            }

            if (x.getExternalDirectory() != null) {
                println();
                print0(ucase ? "DEFAULT DIRECTORY " : "default directory ");
                x.getExternalDirectory().accept(this);
            }

            if (x.getExternalDirectoryRecordFormat() != null) {
                println();
                this.indentCount++;
                print0(ucase ? "ACCESS PARAMETERS (" : "access parameters (");
                x.getExternalDirectoryRecordFormat().accept(this);
                this.indentCount--;
                println();
                print(')');
            }

            if (x.getExternalDirectoryLocation().size() > 0) {
                println();
                print0(ucase ? "LOCATION (" : " location(");
                printAndAccept(x.getExternalDirectoryLocation(), ", ");
                print(')');
            }

            this.indentCount--;
            println();
            print(')');

            if (x.getExternalRejectLimit() != null) {
                println();
                print0(ucase ? "REJECT LIMIT " : "reject limit ");
                x.getExternalRejectLimit().accept(this);
            }
        }

        return false;
    }

    @Override
    public boolean visit(OracleCreateTableStatement.OIDIndex x) {
        print0(ucase ? "OIDINDEX" : "oidindex");

        if (x.getName() != null) {
            print(' ');
            x.getName().accept(this);
        }
        print(" (");
        this.indentCount++;
        printOracleSegmentAttributes(x);
        this.indentCount--;
        println();
        print(")");
        return false;
    }

    @Override
    public boolean visit(OracleCreatePackageStatement x) {
        if (x.isOrReplace()) {
            print0(ucase ? "CREATE OR REPLACE PACKAGE " : "create or replace procedure ");
        } else {
            print0(ucase ? "CREATE PACKAGE " : "create procedure ");
        }

        if (x.isBody()) {
            print0(ucase ? "BODY " : "body ");
        }

        x.getName().accept(this);

        if (x.isBody()) {
            println();
            print0(ucase ? "BEGIN" : "begin");
        }

        this.indentCount++;

        List<SQLStatement> statements = x.getStatements();
        for (int i = 0, size = statements.size(); i < size; ++i) {
            println();
            SQLStatement stmt = statements.get(i);
            stmt.accept(this);
        }

        this.indentCount--;

        if (x.isBody() || statements.size() > 0) {
            println();
            print0(ucase ? "END " : "end ");
            x.getName().accept(this);
            print(';');
        }

        return false;
    }

    @Override
    public boolean visit(OracleExecuteImmediateStatement x) {
        print0(ucase ? "EXECUTE IMMEDIATE " : "execute immediate ");
        x.getDynamicSql().accept(this);

        List<SQLExpr> into = x.getInto();
        if (into.size() > 0) {
            print0(ucase ? " INTO " : " into ");
            printAndAccept(into, ", ");
        }

        List<SQLArgument> using = x.getArguments();
        if (using.size() > 0) {
            print0(ucase ? " USING " : " using ");
            printAndAccept(using, ", ");
        }

        List<SQLExpr> returnInto = x.getReturnInto();
        if (returnInto.size() > 0) {
            print0(ucase ? " RETURNNING INTO " : " returnning into ");
            printAndAccept(returnInto, ", ");
        }
        return false;
    }

    @Override
    public boolean visit(OracleTreatExpr x) {
        print0(ucase ? "TREAT (" : "treat (");
        x.getExpr().accept(this);
        print0(ucase ? " AS " : " as ");
        if (x.isRef()) {
            print0(ucase ? "REF " : "ref ");
        }
        x.getType().accept(this);
        print(')');
        return false;
    }

    @Override
    public boolean visit(OracleCreateSynonymStatement x) {
        if (x.isOrReplace()) {
            print0(ucase ? "CREATE OR REPLACE " : "create or replace ");
        } else {
            print0(ucase ? "CREATE " : "create ");
        }

        if (x.isPublic()) {
            print0(ucase ? "PUBLIC " : "public ");
        }

        print0(ucase ? "SYNONYM " : "synonym ");

        x.getName().accept(this);

        print0(ucase ? " FOR " : " for ");
        x.getObject().accept(this);

        return false;
    }

    @Override
    public boolean visit(OracleCreateTypeStatement x) {
        if (x.isOrReplace()) {
            print0(ucase ? "CREATE OR REPLACE TYPE " : "create or replace type ");
        } else {
            print0(ucase ? "CREATE TYPE " : "create type ");
        }

        if (x.isBody()) {
            print0(ucase ? "BODY " : "body ");
        }

        x.getName().accept(this);

        SQLName under = x.getUnder();
        if (under != null) {
            print0(ucase ? " UNDER " : " under ");
            under.accept(this);
        }

        SQLName authId = x.getAuthId();
        if (authId != null) {
            print0(ucase ? " AUTHID " : " authid ");
            authId.accept(this);
        }

        if (x.isForce()) {
            print0(ucase ? "FORCE " : "force ");
        }

        List<SQLParameter> parameters = x.getParameters();
        SQLDataType tableOf = x.getTableOf();

        if (x.isObject()) {
            print0(" AS OBJECT");
        }

        if (parameters.size() > 0) {
            if (x.isParen()) {
                print(" (");
            } else {
                print0(ucase ? " IS" : " is");
            }
            indentCount++;
            println();

            for (int i = 0; i < parameters.size(); ++i) {
                SQLParameter param = parameters.get(i);
                param.accept(this);

                SQLDataType dataType = param.getDataType();

                if (i < parameters.size() - 1) {
                    if (dataType instanceof OracleFunctionDataType
                            && ((OracleFunctionDataType) dataType).getBlock() != null) {
                        // skip
                        println();
                    } else if (dataType instanceof OracleProcedureDataType
                            && ((OracleProcedureDataType) dataType).getBlock() != null) {
                        // skip
                        println();
                    } else {
                        println(", ");
                    }
                }
            }

            indentCount--;
            println();

            if (x.isParen()) {
                print0(")");
            } else {
                print0("END");
            }
        } else if (tableOf != null) {
            print0(ucase ? " AS TABLE OF " : " as table of ");
            tableOf.accept(this);
        } else if (x.getVarraySizeLimit() != null) {
            print0(ucase ? " VARRAY (" : " varray (");
            x.getVarraySizeLimit().accept(this);
            print0(ucase ? ") OF " : ") of ");
            x.getVarrayDataType().accept(this);
        }

        Boolean isFinal = x.getFinal();
        if (isFinal != null) {
            if (isFinal.booleanValue()) {
                print0(ucase ? " FINAL" : " final");
            } else {
                print0(ucase ? " NOT FINAL" : " not final");
            }
        }

        Boolean instantiable = x.getInstantiable();
        if (instantiable != null) {
            if (instantiable.booleanValue()) {
                print0(ucase ? " INSTANTIABLE" : " instantiable");
            } else {
                print0(ucase ? " NOT INSTANTIABLE" : " not instantiable");
            }
        }

        return false;
    }

    @Override
    public boolean visit(OraclePipeRowStatement x) {
        print0(ucase ? "PIPE ROW(" : "pipe row(");
        printAndAccept(x.getParameters(), ", ");
        print(')');
        return false;
    }

    public boolean visit(OraclePrimaryKey x) {
        visit((SQLPrimaryKey) x);
        return false;
    }

    @Override
    public boolean visit(SQLCreateTableStatement x) {
        int curLen = this.appender.length();
        if (curLen > 0) {
            char c = this.appender.charAt(curLen - 1);
            if (!(c == ' ' || c == '\t' || c == '\n' || c == '\r')) {
                this.appender.append(" ");
            }
        }

        return super.visit(x);
    }

    @Override
    public boolean visit(OracleCreateTableStatement x) {
        printCreateTable(x, false);

        if (x.getOf() != null) {
            println();
            print0(ucase ? "OF " : "of ");
            x.getOf().accept(this);
        }

        if (x.getOidIndex() != null) {
            println();
            x.getOidIndex().accept(this);
        }

        if (x.getOrganization() != null) {
            println();
            this.indentCount++;
            x.getOrganization().accept(this);
            this.indentCount--;
        }

        printOracleSegmentAttributes(x);

        if (x.isInMemoryMetadata()) {
            println();
            print0(ucase ? "IN_MEMORY_METADATA" : "in_memory_metadata");
        }

        if (x.isCursorSpecificSegment()) {
            println();
            print0(ucase ? "CURSOR_SPECIFIC_SEGMENT" : "cursor_specific_segment");
        }

        if (Boolean.TRUE.equals(x.getParallel())) {
            println();
            print0(ucase ? "PARALLEL" : "parallel");
        } else if (Boolean.FALSE.equals(x.getParallel())) {
            println();
            print0(ucase ? "NOPARALLEL" : "noparallel");
        }

        if (Boolean.TRUE.equals(x.getCache())) {
            println();
            print0(ucase ? "CACHE" : "cache");
        } else if (Boolean.FALSE.equals(x.getCache())) {
            println();
            print0(ucase ? "NOCACHE" : "nocache");
        }

        if (x.getLobStorage() != null) {
            println();
            x.getLobStorage().accept(this);
        }

        if (x.isOnCommitPreserveRows()) {
            println();
            print0(ucase ? "ON COMMIT PRESERVE ROWS" : "on commit preserve rows");
        } else if (x.isOnCommitDeleteRows()) {
            println();
            print0(ucase ? "ON COMMIT DELETE ROWS" : "on commit delete rows");
        }

        if (x.isMonitoring()) {
            println();
            print0(ucase ? "MONITORING" : "monitoring");
        }

        printPartitionBy(x);

        if (x.getCluster() != null) {
            println();
            print0(ucase ? "CLUSTER " : "cluster ");
            x.getCluster().accept(this);
            print0(" (");
            printAndAccept(x.getClusterColumns(), ",");
            print0(")");
        }

        if (x.getSelect() != null) {
            println();
            print0(ucase ? "AS" : "as");
            println();
            x.getSelect().accept(this);
        }
        return false;
    }

    @Override
    public boolean visit(OracleStorageClause x) {
        return false;
    }

    @Override
    public boolean visit(OracleGotoStatement x) {
        print0(ucase ? "GOTO " : "GOTO ");
        x.getLabel().accept(this);
        return false;
    }

    @Override
    public boolean visit(OracleLabelStatement x) {
        print0("<<");
        x.getLabel().accept(this);
        print0(">>");
        return false;
    }

    @Override
    public boolean visit(OracleAlterTriggerStatement x) {
        print0(ucase ? "ALTER TRIGGER " : "alter trigger ");
        x.getName().accept(this);

        if (x.isCompile()) {
            print0(ucase ? " COMPILE" : " compile");
        }

        if (x.getEnable() != null) {
            if (x.getEnable().booleanValue()) {
                print0(ucase ? "ENABLE" : "enable");
            } else {
                print0(ucase ? "DISABLE" : "disable");
            }
        }
        return false;
    }

    @Override
    public boolean visit(OracleAlterSynonymStatement x) {
        print0(ucase ? "ALTER SYNONYM " : "alter synonym ");
        x.getName().accept(this);

        if (x.isCompile()) {
            print0(ucase ? " COMPILE" : " compile");
        }

        if (x.getEnable() != null) {
            if (x.getEnable().booleanValue()) {
                print0(ucase ? "ENABLE" : "enable");
            } else {
                print0(ucase ? "DISABLE" : "disable");
            }
        }
        return false;
    }

    @Override
    public boolean visit(OracleAlterViewStatement x) {
        print0(ucase ? "ALTER VIEW " : "alter view ");
        x.getName().accept(this);

        if (x.isCompile()) {
            print0(ucase ? " COMPILE" : " compile");
        }

        if (x.getEnable() != null) {
            if (x.getEnable().booleanValue()) {
                print0(ucase ? "ENABLE" : "enable");
            } else {
                print0(ucase ? "DISABLE" : "disable");
            }
        }
        return false;
    }

    @Override
    public boolean visit(OracleAlterTableMoveTablespace x) {
        print0(ucase ? " MOVE TABLESPACE " : " move tablespace ");
        x.getName().accept(this);
        return false;
    }

    public boolean visit(OracleForeignKey x) {
        visit((SQLForeignKeyImpl) x);
        return false;
    }

    public boolean visit(OracleUnique x) {
        visit((SQLUnique) x);
        return false;
    }

    public boolean visit(OracleSelectSubqueryTableSource x) {
        print('(');
        this.indentCount++;
        println();
        x.getSelect().accept(this);
        this.indentCount--;
        println();
        print(')');

        if (x.getPivot() != null) {
            println();
            x.getPivot().accept(this);
        }

        SQLUnpivot unpivot = x.getUnpivot();
        if (unpivot != null) {
            println();
            unpivot.accept(this);
        }

        printFlashback(x.getFlashback());

        if ((x.getAlias() != null) && (x.getAlias().length() != 0)) {
            print(' ');
            print0(x.getAlias());
        }

        return false;
    }

    @Override
    public boolean visit(SQLParameter x) {
        SQLName name = x.getName();
        if (x.getDataType() == null) {
            x.getName().accept(this);
            return false;
        }
        if (x.getDataType().getName().equalsIgnoreCase("CURSOR")) {
            x.getName().accept(this);
            print0(ucase ? "CURSOR " : "cursor ");
            print0(ucase ? " FOR" : " for");
            this.indentCount++;
            println();
            SQLSelect select = ((SQLQueryExpr) x.getDefaultValue()).getSubQuery();
            select.accept(this);
            this.indentCount--;

        } else {
            if (x.isMap()) {
                print0(ucase ? "MAP MEMBER " : "map member ");
            } else if (x.isOrder()) {
                print0(ucase ? "ORDER MEMBER " : "order member ");
            } else if (x.isMember()) {
                print0(ucase ? "MEMBER " : "member ");
            }
            SQLDataType dataType = x.getDataType();

            if (DbType.oracle == dbType
                || dataType instanceof OracleFunctionDataType
                || dataType instanceof OracleProcedureDataType) {
                if (dataType instanceof OracleFunctionDataType) {
                    OracleFunctionDataType functionDataType = (OracleFunctionDataType) dataType;
                    visit(functionDataType);
                    return false;
                }

                if (dataType instanceof OracleProcedureDataType) {
                    OracleProcedureDataType procedureDataType = (OracleProcedureDataType) dataType;
                    visit(procedureDataType);
                    return false;
                }

                String dataTypeName = dataType.getName();
                boolean printType = (dataTypeName.startsWith("TABLE OF") && x.getDefaultValue() == null)
                                    || dataTypeName.equalsIgnoreCase("REF CURSOR")
                                    || dataTypeName.startsWith("VARRAY(");
                if (printType) {
                    print0(ucase ? "TYPE " : "type ");
                }

                //枚举类型特殊处理
                if ("ENUM".equals(dataTypeName)) {
                    dataType.accept(this);
                    return false;
                } else {
                    name.accept(this);
                }
                if (x.getParamType() == SQLParameter.ParameterType.IN) {
                    print0(ucase ? " IN " : " in ");
                } else if (x.getParamType() == SQLParameter.ParameterType.OUT) {
                    print0(ucase ? " OUT " : " out ");
                } else if (x.getParamType() == SQLParameter.ParameterType.INOUT) {
                    print0(ucase ? " IN OUT " : " in out ");
                } else {
                    print(' ');
                }

                if (x.isNoCopy()) {
                    print0(ucase ? "NOCOPY " : "nocopy ");
                }

                if (x.isConstant()) {
                    print0(ucase ? "CONSTANT " : "constant ");
                }

                if (printType) {
                    print0(ucase ? "IS " : "is ");
                }
            } else {
                if (x.getParamType() == SQLParameter.ParameterType.IN) {
                    boolean skip = DbType.mysql == dbType
                                   && x.getParent() instanceof SQLCreateFunctionStatement;

                    if (!skip) {
                        print0(ucase ? "IN " : "in ");
                    }
                } else if (x.getParamType() == SQLParameter.ParameterType.OUT) {
                    print0(ucase ? "OUT " : "out ");
                } else if (x.getParamType() == SQLParameter.ParameterType.INOUT) {
                    print0(ucase ? "INOUT " : "inout ");
                }
                x.getName().accept(this);
                print(' ');
            }

            dataType.accept(this);

            printParamDefaultValue(x);
        }

        return false;
    }

    @Override
    public boolean visit(SQLUnpivot x) {
        print0(ucase ? "UNPIVOT" : "unpivot");
        if (x.getNullsIncludeType() != null) {
            print(' ');
            print0(SQLUnpivot.NullsIncludeType.toString(x.getNullsIncludeType(), ucase));
        }

        print0(" (");
        if (x.getItems().size() == 1) {
            ((SQLExpr) x.getItems().get(0)).accept(this);
        } else {
            print0(" (");
            printAndAccept(x.getItems(), ", ");
            print(')');
        }

        if (x.getPivotFor().size() > 0) {
            print0(ucase ? " FOR " : " for ");
            if (x.getPivotFor().size() == 1) {
                ((SQLExpr) x.getPivotFor().get(0)).accept(this);
            } else {
                print('(');
                printAndAccept(x.getPivotFor(), ", ");
                print(')');
            }
        }

        if (x.getPivotIn().size() > 0) {
            print0(ucase ? " IN (" : " in (");
            printAndAccept(x.getPivotIn(), ", ");
            print(')');
        }

        print(')');
        return false;
    }

    @Override
    public boolean visit(OracleUpdateStatement x) {
        print0(ucase ? "UPDATE " : "update ");

        if (x.getHints().size() > 0) {
            printAndAccept(x.getHints(), ", ");
            print(' ');
        }

        if (x.isOnly()) {
            print0(ucase ? "ONLY (" : "only (");
            x.getTableSource().accept(this);
            print(')');
        } else {
            x.getTableSource().accept(this);
        }

        printAlias(x.getAlias());

        println();

        print0(ucase ? "SET " : "set ");
        for (int i = 0, size = x.getItems().size(); i < size; ++i) {
            if (i != 0) {
                print0(", ");
            }
            x.getItems().get(i).accept(this);
        }

        if (x.getWhere() != null) {
            println();
            print0(ucase ? "WHERE " : "where ");
            this.indentCount++;
            x.getWhere().accept(this);
            this.indentCount--;
        }

        if (x.getReturning().size() > 0) {
            println();
            print0(ucase ? "RETURNING " : "returning ");
            printAndAccept(x.getReturning(), ", ");
            print0(ucase ? " INTO " : " into ");
            printAndAccept(x.getReturningInto(), ", ");
        }

        return false;
    }

    @Override
    public boolean visit(SampleClause x) {
        print0(ucase ? "SAMPLE " : "sample ");

        if (x.isBlock()) {
            print0(ucase ? "BLOCK " : "block ");
        }

        print('(');
        printAndAccept(x.getPercent(), ", ");
        print(')');

        if (x.getSeedValue() != null) {
            print0(ucase ? " SEED (" : " seed (");
            x.getSeedValue().accept(this);
            print(')');
        }

        return false;
    }

    public boolean visit(OracleSelectJoin x) {
        x.getLeft().accept(this);
        SQLTableSource right = x.getRight();

        if (x.getJoinType() == SQLJoinTableSource.JoinType.COMMA) {
            print0(", ");
            x.getRight().accept(this);
        } else {
            boolean isRoot = x.getParent() instanceof SQLSelectQueryBlock;
            if (isRoot) {
                this.indentCount++;
            }

            println();
            print0(ucase ? x.getJoinType().name : x.getJoinType().nameLCase);
            print(' ');

            if (right instanceof SQLJoinTableSource) {
                print('(');
                right.accept(this);
                print(')');
            } else {
                right.accept(this);
            }

            if (isRoot) {
                this.indentCount--;
            }

            if (x.getCondition() != null) {
                print0(ucase ? " ON " : " on ");
                x.getCondition().accept(this);
                print(' ');
                if (x.getAfterCommentsDirect() != null) {
                    printAfterComments(x.getAfterCommentsDirect());
                    println();
                }
            }

            if (x.getUsing().size() > 0) {
                print0(ucase ? " USING (" : " using (");
                printAndAccept(x.getUsing(), ", ");
                print(')');
            }

            printFlashback(x.getFlashback());
        }

        return false;
    }

    @Override
    public boolean visit(OracleSelectRestriction.CheckOption x) {
        print0(ucase ? "CHECK OPTION" : "check option");
        if (x.getConstraint() != null) {
            print0(ucase ? " CONSTRAINT" : " constraint");
            print(' ');
            x.getConstraint().accept(this);
        }
        return false;
    }

    @Override
    public boolean visit(OracleSelectRestriction.ReadOnly x) {
        print0(ucase ? "READ ONLY" : "read only");
        if (x.getConstraint() != null) {
            print0(ucase ? " CONSTRAINT" : " constraint");
            print(' ');
            x.getConstraint().accept(this);
        }
        return false;
    }

    public boolean visit(OracleDeleteStatement x) {
        return visit((SQLDeleteStatement) x);
    }

    private void printFlashback(SQLExpr flashback) {
        if (flashback == null) {
            return;
        }

        println();

        if (flashback instanceof SQLBetweenExpr) {
            flashback.accept(this);
        } else {
            print0(ucase ? "AS OF " : "as of ");
            flashback.accept(this);
        }
    }

    public boolean visit(OracleWithSubqueryEntry x) {
        print0(x.getAlias());

        if (x.getColumns().size() > 0) {
            print0(" (");
            printAndAccept(x.getColumns(), ", ");
            print(')');
        }

        print0(ucase ? " AS " : " as ");
        print('(');
        this.indentCount++;
        println();
        x.getSubQuery().accept(this);
        this.indentCount--;
        println();
        print(')');

        if (x.getSearchClause() != null) {
            println();
            x.getSearchClause().accept(this);
        }

        if (x.getCycleClause() != null) {
            println();
            x.getCycleClause().accept(this);
        }
        return false;
    }

    @Override
    public boolean visit(SearchClause x) {
        print0(ucase ? "SEARCH " : "search ");
        print0(x.getType().name());
        print0(ucase ? " FIRST BY " : " first by ");
        printAndAccept(x.getItems(), ", ");
        print0(ucase ? " SET " : " set ");
        x.getOrderingColumn().accept(this);

        return false;
    }

    @Override
    public boolean visit(CycleClause x) {
        print0(ucase ? "CYCLE " : "cycle ");
        printAndAccept(x.getAliases(), ", ");
        print0(ucase ? " SET " : " set ");
        x.getMark().accept(this);
        print0(ucase ? " TO " : " to ");
        x.getValue().accept(this);
        print0(ucase ? " DEFAULT " : " default ");
        x.getDefaultValue().accept(this);

        return false;
    }

    public boolean visit(OracleAnalytic x) {
        print0(ucase ? "(" : "(");

        boolean space = false;
        if (x.getPartitionBy().size() > 0) {
            print0(ucase ? "PARTITION BY " : "partition by ");
            printAndAccept(x.getPartitionBy(), ", ");

            space = true;
        }

        SQLOrderBy orderBy = x.getOrderBy();
        if (orderBy != null) {
            if (space) {
                print(' ');
            }
            visit(orderBy);
            space = true;
        }

        OracleAnalyticWindowing windowing = x.getWindowing();
        if (windowing != null) {
            if (space) {
                print(' ');
            }
            visit(windowing);
        }

        print(')');

        return false;
    }

    public boolean visit(OracleAnalyticWindowing x) {
        print0(x.getType().name().toUpperCase());
        print(' ');
        x.getExpr().accept(this);
        return false;
    }

    @Override
    public boolean visit(OracleIsOfTypeExpr x) {
        printExpr(x.getExpr());
        print0(ucase ? " IS OF TYPE (" : " is of type (");

        List<SQLExpr> types = x.getTypes();
        for (int i = 0, size = types.size(); i < size; ++i) {
            if (i != 0) {
                print0(", ");
            }
            SQLExpr type = types.get(i);
            if (Boolean.TRUE.equals(type.getAttribute("ONLY"))) {
                print0(ucase ? "ONLY " : "only ");
            }
            type.accept(this);
        }

        print(')');
        return false;
    }

    @Override
    public boolean visit(OracleRunStatement x) {
        print0("@@");
        printExpr(x.getExpr());
        return false;
    }

    @Override
    public boolean visit(OracleXmlColumnProperties x) {
        return false;
    }

    @Override
    public boolean visit(SQLIfStatement.Else x) {
        print0(ucase ? "ELSE" : "else");
        this.indentCount++;
        println();

        for (int i = 0, size = x.getStatements().size(); i < size; ++i) {
            if (i != 0) {
                println();
            }
            SQLStatement item = x.getStatements().get(i);
            item.accept(this);
        }

        this.indentCount--;
        return false;
    }

    @Override
    public boolean visit(SQLIfStatement.ElseIf x) {
        print0(ucase ? "ELSIF " : "elsif ");
        x.getCondition().accept(this);
        print0(ucase ? " THEN" : " then");
        this.indentCount++;

        for (int i = 0, size = x.getStatements().size(); i < size; ++i) {
            println();
            SQLStatement item = x.getStatements().get(i);
            item.accept(this);
        }

        this.indentCount--;
        return false;
    }

    @Override
    public boolean visit(SQLIfStatement x) {
        print0(ucase ? "IF " : "if ");
        int lines = this.lines;
        this.indentCount++;
        x.getCondition().accept(this);
        this.indentCount--;

        if (lines != this.lines) {
            println();
        } else {
            print(' ');
        }
        print0(ucase ? "THEN" : "then");

        this.indentCount++;
        for (int i = 0, size = x.getStatements().size(); i < size; ++i) {
            println();
            SQLStatement item = x.getStatements().get(i);
            item.accept(this);
        }
        this.indentCount--;

        for (SQLIfStatement.ElseIf elseIf : x.getElseIfList()) {
            println();
            elseIf.accept(this);
        }

        if (x.getElseItem() != null) {
            println();
            x.getElseItem().accept(this);
        }
        println();
        print0(ucase ? "END IF" : "end if");
        return false;
    }

    @Override
    public boolean visit(SQLCreateIndexStatement x) {
        print0(ucase ? "CREATE " : "create ");
        if (x.getType() != null) {
            print0(x.getType());
            print(' ');
        }

        print0(ucase ? "INDEX" : "index");

        if (x.isIfNotExists()) {
            print0(ucase ? " IF NOT EXISTS" : " if not exists");
        }

        if (x.isConcurrently()) {
            print0(ucase ? " CONCURRENTLY" : " concurrently");
        }

        SQLName name = x.getName();
        if (name != null) {
            print(' ');
            name.accept(this);
        }

        print0(ucase ? " ON " : " on ");
        x.getTable().accept(this);

        if (x.getUsing() != null) {
            print0(ucase ? " USING " : " using ");
            print0(x.getUsing());
        }
        print0(" (");
        printAndAccept(x.getItems(), ", ");
        print(')');

        SQLExpr comment = x.getComment();
        if (comment != null) {
            print0(ucase ? " COMMENT " : " comment ");
            comment.accept(this);
        }

        boolean hasOptions = false;

        if (x.getIndexDefinition().hasOptions()) {
            SQLIndexOptions indexOptions = x.getIndexDefinition().getOptions();
            if (indexOptions.getKeyBlockSize() != null ||
                    indexOptions.getParserName() != null ||
                    indexOptions.getAlgorithm() != null ||
                    indexOptions.getLock() != null ||
                    indexOptions.getOtherOptions().size() > 0) {
                hasOptions = true;
            }
        }

        if (hasOptions) {
            print0(ucase ? " WITH (" : " with (");
            SQLIndexOptions indexOptions = x.getIndexDefinition().getOptions();

            SQLExpr keyBlockSize = indexOptions.getKeyBlockSize();
            if (keyBlockSize != null) {
                print0(ucase ? " KEY_BLOCK_SIZE = " : " key_block_size = ");
                printExpr(keyBlockSize, parameterized);
            }

            String parserName = indexOptions.getParserName();
            if (parserName != null) {
                print0(ucase ? " WITH PARSER " : " with parser ");
                print0(parserName);
            }

            String algorithm = indexOptions.getAlgorithm();
            if (algorithm != null) {
                print0(ucase ? " ALGORITHM = " : " algorithm = ");
                print0(algorithm);
            }

            String lock = indexOptions.getLock();
            if (lock != null) {
                print0(ucase ? " LOCK " : " lock ");
                print0(lock);
            }

            for (SQLAssignItem option : indexOptions.getOtherOptions()) {
                option.accept(this);
            }

            print(')');
        }

        SQLName tablespace = x.getTablespace();
        if (tablespace != null) {
            print0(ucase ? " TABLESPACE " : " tablespace ");
            tablespace.accept(this);
        }

        return false;
    }

    @Override
    public boolean visit(SQLAlterTableAddColumn x) {
        boolean odps = isOdps();
        print0(ucase ? "ADD COLUMN " : "add column ");
        printAndAccept(x.getColumns(), ", ");
        return false;
    }

    @Override
    public boolean visit(OracleXmlColumnProperties.OracleXMLTypeStorage x) {
        return false;
    }

    public boolean visit(SQLArrayDataType x) {
        SQLDataType componentType = x.getComponentType();
        if (componentType != null) {
            componentType.accept(this);
        }
        print('[');
        printAndAccept(x.getArguments(), ", ");
        print(']');
        return false;
    }

    public boolean visit(SQLCharExpr x, boolean parameterized) {
        if (parameterized) {
            print('?');
            incrementReplaceCunt();
            if (this.parameters != null) {
                ExportParameterVisitorUtils.exportParameter(this.parameters, x);
            }
            return false;
        }

        if (x instanceof PGCharExpr && ((PGCharExpr) x).isCSytle()) {
            print('E');
        }
        if (x.getCollate() != null) {
            String collate = x.getCollate();
            if (x.isParenthesized()) {
                print('(');
            }
            printChars(x.getText());
            print(" COLLATE ");
            if (collate.startsWith("'") || collate.endsWith("\"")) {
                print(collate);
            } else {
                print('\'');
                print(collate);
                print('\'');
            }
            if (x.isParenthesized()) {
                print(')');
            }
        } else {
            printChars(x.getText());
        }

        return false;
    }

    @Override
    public boolean visit(PGAnalyzeStatement x) {
        print0(ucase ? "ANALYZE " : "analyze");
        if (x.isVerbose()) {
            print0(ucase ? "VERBOSE " : "verbose ");
        }
        if (x.isSkipLocked()) {
            print0(ucase ? "SKIP_LOCKED " : "skip_locked ");
        }
        printAndAccept(x.getTableSources(), ", ");
        return false;
    }

    @Override
    public boolean visit(PGVacuumStatement x) {
        print0(ucase ? "VACUUM " : "vacuum");
        if (x.isFull()) {
            print0(ucase ? "FULL " : "full ");
        }
        if (x.isFreeze()) {
            print0(ucase ? "FREEZE " : "freeze ");
        }
        if (x.isVerbose()) {
            print0(ucase ? "VERBOSE " : "verbose ");
        }
        if (x.isAnalyze()) {
            print0(ucase ? "ANALYZE " : "analyze ");
        }
        if (x.isDisablePageSkipping()) {
            print0(ucase ? "DISABLE_PAGE_SKIPPING " : "disable_page_skipping ");
        }
        if (x.isSkipLocked()) {
            print0(ucase ? "SKIP_LOCKED " : "skip_locked ");
        }
        if (x.isProcessToast()) {
            print0(ucase ? "PROCESS_TOAST " : "process_toast ");
        }
        if (x.isTruncate()) {
            print0(ucase ? "TRUNCATE " : "truncate ");
        }
        printVacuumRest(x);
        printAndAccept(x.getTableSources(), ", ");
        return false;
    }

    protected void printVacuumRest(PGVacuumStatement x){
    }

    @Override
    public boolean visit(PGAlterDatabaseStatement x) {
        print0(ucase ? "ALTER DATABASE " : "alter database ");
        x.getDatabaseName().accept(this);
        print(' ');
        if (x.getRenameToName() != null) {
            print0(ucase ? "RENAME TO " : "rename to ");
            x.getRenameToName().accept(this);
        }
        if (x.getOwnerToName() != null) {
            print0(ucase ? "OWNER TO " : "owner to ");
            x.getOwnerToName().accept(this);
        }

        if (x.getSetTableSpaceName() != null) {
            print0(ucase ? "SET TABLESPACE " : "set tablespace ");
            x.getSetTableSpaceName().accept(this);
        }
        if (x.isRefreshCollationVersion()) {
            print0(ucase ? "REFRESH COLLATION VERSION " : "refresh collation version ");
        }
        if (x.getSetParameterName() != null) {
            print0(ucase ? "SET " : "set ");
            x.getSetParameterName().accept(this);
            if (x.isUseEquals()) {
                print(" = ");
            } else {
                print0(ucase ? " TO " : " to ");
            }
            x.getSetParameterValue().accept(this);
        }
        if (x.getResetParameterName() != null) {
            print0(ucase ? "RESET " : "reset ");
            x.getResetParameterName().accept(this);
        }
        if (x.isHaveWith()) {
            print0(ucase ? "WITH " : "with ");
        }
        if (x.getAllowConnections() != null) {
            print0(ucase ? "ALLOW_CONNECTIONS " : "allow_connections ");
            print0(String.valueOf(x.getAllowConnections()));
        }
        if (x.getSetTemplateMark() != null) {
            print0(ucase ? "IS_TEMPLATE " : "is_template ");
            print0(String.valueOf(x.getSetTemplateMark()));
        }
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableChangeOwner x) {
        print0(ucase ? "OWNER TO " : "owner to ");
        print0(x.getOwner().getSimpleName());
        return false;
    }

    @Override
    protected void printAutoIncrement() {
        print0(ucase ? " GENERATED ALWAYS AS IDENTITY" : " generated always as identity");
    }

    @Override
    protected void printGeneratedAlways(SQLColumnDefinition x, boolean parameterized) {
        SQLExpr generatedAlwaysAs = x.getGeneratedAlwaysAs();
        SQLColumnDefinition.Identity identity = x.getIdentity();

        if (generatedAlwaysAs != null || identity != null) {
            if (x.isGenerateByDefault()) {
                print0(ucase ? " GENERATED BY DEFAULT AS " : " generated by default as ");
            } else {
                print0(ucase ? " GENERATED ALWAYS AS " : " generated always as ");
            }
            if (generatedAlwaysAs != null) {
                printExpr(generatedAlwaysAs, parameterized);
                print(' ');
            }
            identity.accept(this);
        }
    }

    @Override
    public boolean visit(SQLColumnDefinition.Identity x) {
        print0(ucase ? "IDENTITY" : "identity");
        Integer seed = x.getSeed();
        if (seed != null) {
            print0(ucase ? " (INCREMENT BY " : " (increment by ");
            if (x.getIncrement() != null) {
                print(x.getIncrement());
            } else {
                print('1');
            }
            print0(ucase ? " START WITH  " : " start with  ");
            print(seed);
            print(')');
        }
        return false;
    }

    @Override
    public boolean visit(SQLIntervalExpr x) {
        print0(ucase ? "INTERVAL " : "interval ");
        SQLExpr value = x.getValue();

        boolean str = value instanceof SQLCharExpr;
        if (!str) {
            print('\'');
        }
        value.accept(this);

        SQLIntervalUnit unit = x.getUnit();
        if (unit != null) {
            print(' ');
            print0(ucase ? unit.name : unit.nameLCase);
        }
        if (!str) {
            print('\'');
        }
        return false;
    }

    public boolean visit(SQLAlterTableAlterColumn x) {
        super.visit(x);
        if (x.getUsing() != null) {
            print0(ucase ? " USING " : " using ");
            x.getUsing().accept(this);
        }
        return false;
    }

    @Override
    protected void printTableOptionsPrefix(SQLCreateTableStatement x) {
        println();
        print0(ucase ? "WITH (" : "with (");
        incrementIndent();
        println();
    }

    protected boolean legacyCube() {
        return true;
    }

    protected void printTableOption(SQLExpr name, SQLExpr value, int index) {
        if (index != 0) {
            print(",");
            println();
        }
        String key = name.toString();
        print0(key);
        print0(" = ");
        value.accept(this);
    }
}
