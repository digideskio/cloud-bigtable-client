/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.cloud.bigtable.grpc.scanner;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import com.google.bigtable.v2.Row;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.protobuf.ByteString;

/**
 * <p>
 * This class stores represents a single row. It's a flattened version of the data of a
 * {@link Row}
 * </p>
 * @author tyagihas
 * @version $Id: $Id
 */
public class FlatRow implements Serializable {

  private static final long serialVersionUID = 1L;

  public final static class Cell implements Serializable {

    private static final long serialVersionUID = 1L;

    public final static class Builder {
      private String family;
      private ByteString qualifier;
      private long timestamp;
      private ByteString value;
      private List<String> labels;

      public Builder withFamily(String family) {
        this.family = family;
        return this;
      }

      public Builder withQualifier(ByteString qualifier) {
        this.qualifier = qualifier;
        return this;
      }

      public Builder withTimestamp(long timestamp) {
        this.timestamp = timestamp;
        return this;
      }

      public Builder withValue(ByteString value) {
        this.value = value;
        return this;
      }

      public Builder withLabels(List<String> labels) {
        if (labels == null || labels.isEmpty() ) {
          return this;
        }
        this.labels = labels;
        return this;
      }

      public Cell build() {
        return new Cell(family, qualifier, timestamp, value, labels);
      }
    }

    public static Builder newBuilder() {
      return new Builder();
    }

    private final String family;
    private final ByteString qualifier;
    private final long timestamp;
    private final ByteString value;
    private final List<String> labels;

    private Cell(String family, ByteString qualifier, long timestamp, ByteString value,
        List<String> labels) {
      this.family = family;
      this.qualifier = qualifier;
      this.timestamp = timestamp;
      this.value = value;
      this.labels = labels == null ? Collections.<String> emptyList() : labels;
    }

    public String getFamily() {
      return family;
    }

    public ByteString getQualifier() {
      return qualifier;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public ByteString getValue() {
      return value;
    }

    public List<String> getLabels() {
      return labels;
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof Cell)) {
        return false;
      }
      Cell other = (Cell) obj;
      return equalFamilyQualifierAndTimestamp(other) &&
          Objects.equal(value, other.value) &&
          Objects.equal(labels, other.labels);
    }

    /**
     * @param other
     * @return true if the family, qualifier and timestamps are the same in this Cell as it is in
     *         the other Cell.
     */
    public boolean equalFamilyQualifierAndTimestamp(Cell other) {
      return other != null &&
          Objects.equal(family, other.family) &&
          Objects.equal(qualifier, other.qualifier) &&
          timestamp == other.timestamp;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("family", family)
          .add("qualifier", qualifier)
          .add("timestamp", timestamp)
          .add("value", value)
          .add("labels", labels)
          .toString();
    }
  }

  public static class CellOrdering extends Ordering<Cell> {
    public static final CellOrdering DEFAULT_ORDERING = new CellOrdering();

    private CellOrdering() {}

    @Override
    public int compare(Cell left, Cell right) {

      int result = left.family.compareTo(right.family);
      if (result != 0) {
        return result;
      }
      result =
          left.qualifier.asReadOnlyByteBuffer().compareTo(right.qualifier.asReadOnlyByteBuffer());
      if (result != 0) {
        return result;
      }
      return 0;
    }
  }

  public static final class Builder {
    private ByteString rowKey = null;
    private final ImmutableList.Builder<Cell> listBuilder;

    private Builder() {
      listBuilder = new ImmutableList.Builder<Cell>();
    }

    public Builder withRowKey(ByteString rowKey) {
      Preconditions.checkNotNull(rowKey, "Row Key can not be null");
      this.rowKey = rowKey;
      return this;
    }

    public Builder addCell(String family, ByteString qualifier, long timestamp, ByteString value,
        List<String> labels) {
      return addCell(new Cell(family, qualifier, timestamp, value, labels));
    }

    public Builder addCell(String family, ByteString qualifier, long timestamp, ByteString value) {
      return addCell(new Cell(family, qualifier, timestamp, value, null));
    }

    public Builder addCell(Cell column) {
      Preconditions.checkNotNull(column, "column can not be null");
      listBuilder.add(column);
      return this;
    }

    public FlatRow build() {
      return new FlatRow(rowKey, listBuilder.build());
    }

    public ByteString getRowKey() {
      return rowKey;
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  private final ByteString rowKey;
  private final ImmutableList<Cell> cells;

  public FlatRow(ByteString rowKey, ImmutableList<Cell> cells) {
    this.rowKey = rowKey;
    this.cells = cells;
  }

  public ByteString getRowKey() {
    return rowKey;
  }

  public List<Cell> getCells() {
    return cells;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof FlatRow)) {
      return false;
    }
    FlatRow other = (FlatRow) obj;
    return Objects.equal(rowKey, other.rowKey) &&
        Objects.equal(cells, other.getCells());
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("rowKey", rowKey)
        .add("cells", cells)
        .toString();
  }
}