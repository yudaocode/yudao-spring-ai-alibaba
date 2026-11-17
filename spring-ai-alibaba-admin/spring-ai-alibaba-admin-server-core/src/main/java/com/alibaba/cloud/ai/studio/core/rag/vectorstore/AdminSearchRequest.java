/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.studio.core.rag.vectorstore;

import java.util.Objects;

import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.util.Assert;

/**
 * Adds Admin pagination and hybrid-search settings without replacing Spring AI classes.
 *
 * @author 芋道源码
 * @since 1.1.2.2
 */
public final class AdminSearchRequest extends SearchRequest {

	private final int from;

	private final SearchType searchType;

	private final float hybridWeight;

	private AdminSearchRequest(Builder builder) {
		super(builder.delegate.build());
		this.from = builder.from;
		this.searchType = builder.searchType;
		this.hybridWeight = builder.hybridWeight;
	}

	public static Builder adminBuilder() {
		return new Builder();
	}

	public static Builder copyOf(SearchRequest original) {
		Assert.notNull(original, "original request must not be null");
		Builder builder = adminBuilder().query(original.getQuery())
			.topK(original.getTopK())
			.similarityThreshold(original.getSimilarityThreshold())
			.filterExpression(original.getFilterExpression());
		if (original instanceof AdminSearchRequest) {
			AdminSearchRequest admin = (AdminSearchRequest) original;
			builder.from(admin.from).searchType(admin.searchType).hybridWeight(admin.hybridWeight);
		}
		return builder;
	}

	public int getFrom() {
		return this.from;
	}

	public SearchType getSearchType() {
		return this.searchType;
	}

	public float getHybridWeight() {
		return this.hybridWeight;
	}

	@Override
	public boolean equals(Object other) {
		if (!super.equals(other)) {
			return false;
		}
		AdminSearchRequest that = (AdminSearchRequest) other;
		return this.from == that.from && this.searchType == that.searchType
				&& Float.compare(this.hybridWeight, that.hybridWeight) == 0;
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(), this.from, this.searchType, this.hybridWeight);
	}

	/**
	 * Builds a request using the library's validation for shared fields.
	 *
	 * @author 芋道源码
	 */
	public static final class Builder {

		private final SearchRequest.Builder delegate = SearchRequest.builder().similarityThreshold(0.4);

		private int from;

		private SearchType searchType = SearchType.SEMANTIC;

		private float hybridWeight = 0.7f;

		private Builder() {
		}

		public Builder query(String query) {
			this.delegate.query(query);
			return this;
		}

		public Builder topK(int topK) {
			this.delegate.topK(topK);
			return this;
		}

		public Builder similarityThreshold(double threshold) {
			this.delegate.similarityThreshold(threshold);
			return this;
		}

		public Builder similarityThresholdAll() {
			return similarityThreshold(0.0);
		}

		public Builder filterExpression(Filter.Expression filter) {
			this.delegate.filterExpression(filter);
			return this;
		}

		public Builder filterExpression(String filter) {
			this.delegate.filterExpression(filter);
			return this;
		}

		public Builder from(int from) {
			this.from = from;
			return this;
		}

		public Builder searchType(SearchType searchType) {
			this.searchType = searchType;
			return this;
		}

		public Builder hybridWeight(float hybridWeight) {
			this.hybridWeight = hybridWeight;
			return this;
		}

		public AdminSearchRequest build() {
			return new AdminSearchRequest(this);
		}

	}

}
