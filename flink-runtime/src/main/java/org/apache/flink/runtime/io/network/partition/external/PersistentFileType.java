/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.io.network.partition.external;

/**
 * Type of files created by a result partition.
 */
public enum PersistentFileType {
	/**
	 *  Undefined external file type
	 */
	UNDEFINED,

	/**
	 *  Persistent file generated by hash writer. For this type, each subpartition has a
	 *  standalone data file.
	 */
	HASH_PARTITION_FILE,

	/**
	 *  Persistent file generated by spill-merge writer. For this type, all the data files
	 *  contain data from different subpartitions, and the data belongs to the same partition
	 *  is stored continuously.
	 */
	MERGED_PARTITION_FILE,
}
