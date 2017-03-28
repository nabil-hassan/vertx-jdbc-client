/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.ext.jdbc.impl.actions;

import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.TaskQueue;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.UpdateResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class JDBCUpdate extends AbstractJDBCAction<UpdateResult> {

  private static final Pattern regex = Pattern.compile("(^|\\s)insert(\\s|$)", Pattern.CASE_INSENSITIVE + Pattern.MULTILINE);

  private final String sql;
  private final JsonArray in;
  private final int timeout;
  private final List<String> generatedKeyColumns;

  public JDBCUpdate(Vertx vertx, JDBCStatementHelper helper, Connection connection, ContextInternal ctx, TaskQueue statementsQueue, int timeout, String sql, JsonArray in, List<String> generatedKeyColumns) {
    super(vertx, helper, connection, ctx, statementsQueue);
    this.sql = sql;
    this.in = in;
    this.generatedKeyColumns = generatedKeyColumns;
    this.timeout = timeout;
  }

  @Override
  protected UpdateResult execute() throws SQLException {
    final boolean returnGeneratedKeys = regex.matcher(sql).groupCount() == 2;
    try (PreparedStatement statement = (generatedKeyColumns != null && generatedKeyColumns.size() > 0)
      ? conn.prepareStatement(sql, generatedKeyColumns.toArray(new String[0]))
      : conn.prepareStatement(sql, returnGeneratedKeys ? Statement.RETURN_GENERATED_KEYS : Statement.NO_GENERATED_KEYS)) {
      if (timeout >= 0) {
        statement.setQueryTimeout(timeout);
      }

      helper.fillStatement(statement, in);

      int updated = statement.executeUpdate();
      JsonArray keys = new JsonArray();

      // Create JsonArray of keys
      if (returnGeneratedKeys) {
        ResultSet rs = null;
        try {
          // the resource might also fail
          // specially on oracle DBMS
          rs = statement.getGeneratedKeys();
          if (rs != null) {
            while (rs.next()) {
              Object key = rs.getObject(1);
              if (key != null) {
                keys.add(helper.convertSqlValue(key));
              }
            }
          }
        } catch (SQLException e) {
          // do not crash if no permissions
        } finally {
          if (rs != null) {
            try {
              rs.close();
            } catch (SQLException e) {
              // ignore close error
            }
          }
        }
      }

      return new UpdateResult(updated, keys);
    }
  }

  @Override
  protected String name() {
    return "update";
  }
}
