// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: google/bigtable/admin/v2/bigtable_table_admin.proto

package com.google.bigtable.admin.v2;

public interface CreateTableFromSnapshotRequestOrBuilder extends
    // @@protoc_insertion_point(interface_extends:google.bigtable.admin.v2.CreateTableFromSnapshotRequest)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>optional string parent = 1;</code>
   *
   * <pre>
   * The unique name of the instance in which to create the table.
   * Values are of the form `projects/&lt;project&gt;/instances/&lt;instance&gt;`.
   * </pre>
   */
  java.lang.String getParent();
  /**
   * <code>optional string parent = 1;</code>
   *
   * <pre>
   * The unique name of the instance in which to create the table.
   * Values are of the form `projects/&lt;project&gt;/instances/&lt;instance&gt;`.
   * </pre>
   */
  com.google.protobuf.ByteString
      getParentBytes();

  /**
   * <code>optional string table_id = 2;</code>
   *
   * <pre>
   * The name by which the new table should be referred to within the parent
   * instance, e.g., `foobar` rather than `&lt;parent&gt;/tables/foobar`.
   * </pre>
   */
  java.lang.String getTableId();
  /**
   * <code>optional string table_id = 2;</code>
   *
   * <pre>
   * The name by which the new table should be referred to within the parent
   * instance, e.g., `foobar` rather than `&lt;parent&gt;/tables/foobar`.
   * </pre>
   */
  com.google.protobuf.ByteString
      getTableIdBytes();

  /**
   * <code>optional string source_snapshot = 3;</code>
   *
   * <pre>
   * The unique name of the snapshot from which to restore the table. The
   * snapshot and the table must be in the same instance.
   * Values are of the form
   * `projects/&lt;project&gt;/instances/&lt;instance&gt;/clusters/&lt;cluster&gt;/snapshots/&lt;snapshot&gt;`.
   * </pre>
   */
  java.lang.String getSourceSnapshot();
  /**
   * <code>optional string source_snapshot = 3;</code>
   *
   * <pre>
   * The unique name of the snapshot from which to restore the table. The
   * snapshot and the table must be in the same instance.
   * Values are of the form
   * `projects/&lt;project&gt;/instances/&lt;instance&gt;/clusters/&lt;cluster&gt;/snapshots/&lt;snapshot&gt;`.
   * </pre>
   */
  com.google.protobuf.ByteString
      getSourceSnapshotBytes();
}
