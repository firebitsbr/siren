/**
 * Copyright (c) 2014, Sindice Limited. All Rights Reserved.
 *
 * This file is part of the SIREn project.
 *
 * SIREn is a free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * SIREn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sindicetech.siren.search.node;

import org.apache.lucene.index.*;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.BytesRef;

import com.sindicetech.siren.search.node.MultiNodeTermQuery.RewriteMethod;

import java.io.IOException;
import java.util.Comparator;

/**
 * Abstract class for rewrite methods.
 *
 * <p>
 *
 * Code taken from {@link org.apache.lucene.search.TermCollectingRewrite} and adapted for SIREn.
 */
abstract class NodeTermCollectingRewrite<Q extends Query> extends RewriteMethod {

  /** Return a suitable top-level Query for holding all expanded terms. */
  protected abstract Q getTopLevelQuery(final NodeQuery query) throws IOException;

  /** Add a MultiTermQuery term to the top-level query */
  protected final void addClause(final Q topLevel, final Term term, final int docCount, final float boost)
  throws IOException {
    this.addClause(topLevel, term, docCount, boost, null);
  }

  protected abstract void addClause(Q topLevel, Term term, int docCount,
                                    float boost, TermContext states)
  throws IOException;

  final void collectTerms(final IndexReader reader,
                          final MultiNodeTermQuery query,
                          final TermCollector collector)
  throws IOException {
    final IndexReaderContext topReaderContext = reader.getContext();
    Comparator<BytesRef> lastTermComp = null;
    for (final AtomicReaderContext context : topReaderContext.leaves()) {
      final Fields fields = context.reader().fields();
      if (fields == null) {
        // reader has no fields
        continue;
      }

      final Terms terms = fields.terms(query.field);
      if (terms == null) {
        // field does not exist
        continue;
      }

      final TermsEnum termsEnum = this.getTermsEnum(query, terms, collector.attributes);
      assert termsEnum != null;

      if (termsEnum == TermsEnum.EMPTY)
        continue;

      // Check comparator compatibility:
      final Comparator<BytesRef> newTermComp = termsEnum.getComparator();
      if (lastTermComp != null && newTermComp != null && newTermComp != lastTermComp)
        throw new RuntimeException("term comparator should not change between segments: "+lastTermComp+" != "+newTermComp);
      lastTermComp = newTermComp;
      collector.setReaderContext(topReaderContext, context);
      collector.setNextEnum(termsEnum);
      BytesRef bytes;
      while ((bytes = termsEnum.next()) != null) {
        if (!collector.collect(bytes))
          return; // interrupt whole term collection, so also don't iterate other subReaders
      }
    }
  }

  static abstract class TermCollector  {

    protected AtomicReaderContext readerContext;
    protected IndexReaderContext topReaderContext;

    public void setReaderContext(final IndexReaderContext topReaderContext,
                                 final AtomicReaderContext readerContext) {
      this.readerContext = readerContext;
      this.topReaderContext = topReaderContext;
    }

    /** attributes used for communication with the enum */
    public final AttributeSource attributes = new AttributeSource();

    /** return false to stop collecting */
    public abstract boolean collect(BytesRef bytes) throws IOException;

    /** the next segment's {@link TermsEnum} that is used to collect terms */
    public abstract void setNextEnum(TermsEnum termsEnum) throws IOException;
  }

}
