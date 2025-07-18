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
package com.alibaba.druid.sql.ast;

import com.alibaba.druid.sql.visitor.SQLASTVisitor;

import java.util.List;
import java.util.Map;

public interface SQLObject {
    default int getSourceColumn() {
        return 0;
    }

    default int getSourceLine() {
        return 0;
    }

    default void setSource(int column, int line) {
    }

    void accept(SQLASTVisitor visitor);

    SQLObject clone();

    SQLObject getParent();
    SQLObject getParent(int level);

    void setParent(SQLObject parent);

    Map<String, Object> getAttributes();

    boolean containsAttribute(String name);

    Object getAttribute(String name);

    void putAttribute(String name, Object value);

    Map<String, Object> getAttributesDirect();

    void output(StringBuilder buf);

    void addBeforeComment(String comment);

    void addBeforeComment(List<String> comments);

    List<String> getBeforeCommentsDirect();

    void addAfterComment(String comment);

    void addAfterComment(List<String> comments);

    List<String> getAfterCommentsDirect();

    boolean hasBeforeComment();

    boolean hasAfterComment();
}
