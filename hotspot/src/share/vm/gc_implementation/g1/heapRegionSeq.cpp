/*
 * Copyright (c) 2001, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include "precompiled.hpp"
#include "gc_implementation/g1/heapRegion.hpp"
#include "gc_implementation/g1/heapRegionSeq.inline.hpp"
#include "gc_implementation/g1/heapRegionSets.hpp"
#include "gc_implementation/g1/g1CollectedHeap.inline.hpp"
#include "memory/allocation.hpp"

// Private

uint HeapRegionSeq::find_contiguous_from(uint from, uint num) {
  uint len = length();
  assert(num > 1, "use this only for sequences of length 2 or greater");
  assert(from <= len,
         err_msg("from: %u should be valid and <= than %u", from, len));

  uint curr = from;
  uint first = G1_NULL_HRS_INDEX;
  uint num_so_far = 0;
  while (curr < len && num_so_far < num) {
    if (at(curr)->is_empty()) {
      if (first == G1_NULL_HRS_INDEX) {
        first = curr;
        num_so_far = 1;
      } else {
        num_so_far += 1;
      }
    } else {
      first = G1_NULL_HRS_INDEX;
      num_so_far = 0;
    }
    curr += 1;
  }
  assert(num_so_far <= num, "post-condition");
  if (num_so_far == num) {
    // we found enough space for the humongous object
    assert(from <= first && first < len, "post-condition");
    assert(first < curr && (curr - first) == num, "post-condition");
    for (uint i = first; i < first + num; ++i) {
      assert(at(i)->is_empty(), "post-condition");
    }
    return first;
  } else {
    // we failed to find enough space for the humongous object
    return G1_NULL_HRS_INDEX;
  }
}

// Public

void HeapRegionSeq::initialize(HeapWord* bottom, HeapWord* end,
                               uint max_length) {
  assert((uintptr_t) bottom % HeapRegion::GrainBytes == 0,
         "bottom should be heap region aligned");
  assert((uintptr_t) end % HeapRegion::GrainBytes == 0,
         "end should be heap region aligned");

  _length = 0;
  _heap_bottom = bottom;
  _heap_end = end;
  _region_shift = HeapRegion::LogOfHRGrainBytes;
  _next_search_index = 0;
  _allocated_length = 0;
  _max_length = max_length;

  _regions = NEW_C_HEAP_ARRAY(HeapRegion*, max_length, mtGC);
  memset(_regions, 0, (size_t) max_length * sizeof(HeapRegion*));
  _regions_biased = _regions - ((uintx) bottom >> _region_shift);

  assert(&_regions[0] == &_regions_biased[addr_to_index_biased(bottom)],
         "bottom should be included in the region with index 0");
}

MemRegion HeapRegionSeq::expand_by(HeapWord* old_end,
                                   HeapWord* new_end,
                                   FreeRegionList* list) {
  assert(old_end < new_end, "don't call it otherwise");
  G1CollectedHeap* g1h = G1CollectedHeap::heap();

  HeapWord* next_bottom = old_end;
  assert(_heap_bottom <= next_bottom, "invariant");
  while (next_bottom < new_end) {
    assert(next_bottom < _heap_end, "invariant");
    uint index = length();

    assert(index < _max_length, "otherwise we cannot expand further");
    if (index == 0) {
      // We have not allocated any regions so far
      assert(next_bottom == _heap_bottom, "invariant");
    } else {
      // next_bottom should match the end of the last/previous region
      assert(next_bottom == at(index - 1)->end(), "invariant");
    }

    if (index == _allocated_length) {
      // We have to allocate a new HeapRegion.
      HeapRegion* new_hr = g1h->new_heap_region(index, next_bottom);
      if (new_hr == NULL) {
        // allocation failed, we bail out and return what we have done so far
        return MemRegion(old_end, next_bottom);
      }
      assert(_regions[index] == NULL, "invariant");
      _regions[index] = new_hr;
      increment_length(&_allocated_length);
    }
    // Have to increment the length first, otherwise we will get an
    // assert failure at(index) below.
    increment_length(&_length);
    HeapRegion* hr = at(index);
    list->add_as_tail(hr);

    next_bottom = hr->end();
  }
  assert(next_bottom == new_end, "post-condition");
  return MemRegion(old_end, next_bottom);
}

uint HeapRegionSeq::free_suffix() {
  uint res = 0;
  uint index = length();
  while (index > 0) {
    index -= 1;
    if (!at(index)->is_empty()) {
      break;
    }
    res += 1;
  }
  return res;
}

uint HeapRegionSeq::find_contiguous(uint num) {
  assert(num > 1, "use this only for sequences of length 2 or greater");
  assert(_next_search_index <= length(),
         err_msg("_next_search_index: %u should be valid and <= than %u",
                 _next_search_index, length()));

  uint start = _next_search_index;
  uint res = find_contiguous_from(start, num);
  if (res == G1_NULL_HRS_INDEX && start > 0) {
    // Try starting from the beginning. If _next_search_index was 0,
    // no point in doing this again.
    res = find_contiguous_from(0, num);
  }
  if (res != G1_NULL_HRS_INDEX) {
    assert(res < length(), err_msg("res: %u should be valid", res));
    _next_search_index = res + num;
    assert(_next_search_index <= length(),
           err_msg("_next_search_index: %u should be valid and <= than %u",
                   _next_search_index, length()));
  }
  return res;
}

void HeapRegionSeq::iterate(HeapRegionClosure* blk) const {
  iterate_from((HeapRegion*) NULL, blk);
}

void HeapRegionSeq::iterate_from(HeapRegion* hr, HeapRegionClosure* blk) const {
  uint hr_index = 0;
  if (hr != NULL) {
    hr_index = hr->hrs_index();
  }

  uint len = length();
  for (uint i = hr_index; i < len; i += 1) {
    bool res = blk->doHeapRegion(at(i));
    if (res) {
      blk->incomplete();
      return;
    }
  }
  for (uint i = 0; i < hr_index; i += 1) {
    bool res = blk->doHeapRegion(at(i));
    if (res) {
      blk->incomplete();
      return;
    }
  }
}

MemRegion HeapRegionSeq::shrink_by(size_t shrink_bytes,
                                   uint* num_regions_deleted) {
  // Reset this in case it's currently pointing into the regions that
  // we just removed.
  _next_search_index = 0;

  assert(shrink_bytes % os::vm_page_size() == 0, "unaligned");
  assert(shrink_bytes % HeapRegion::GrainBytes == 0, "unaligned");
  assert(length() > 0, "the region sequence should not be empty");
  assert(length() <= _allocated_length, "invariant");
  assert(_allocated_length > 0, "we should have at least one region committed");

  // around the loop, i will be the next region to be removed
  uint i = length() - 1;
  assert(i > 0, "we should never remove all regions");
  // [last_start, end) is the MemRegion that covers the regions we will remove.
  HeapWord* end = at(i)->end();
  HeapWord* last_start = end;
  *num_regions_deleted = 0;
  while (shrink_bytes > 0) {
    HeapRegion* cur = at(i);
    // We should leave the humongous regions where they are.
    if (cur->isHumongous()) break;
    // We should stop shrinking if we come across a non-empty region.
    if (!cur->is_empty()) break;

    i -= 1;
    *num_regions_deleted += 1;
    shrink_bytes -= cur->capacity();
    last_start = cur->bottom();
    decrement_length(&_length);
    // We will reclaim the HeapRegion. _allocated_length should be
    // covering this index. So, even though we removed the region from
    // the active set by decreasing _length, we still have it
    // available in the future if we need to re-use it.
    assert(i > 0, "we should never remove all regions");
    assert(length() > 0, "we should never remove all regions");
  }
  return MemRegion(last_start, end);
}

#ifndef PRODUCT
void HeapRegionSeq::verify_optional() {
  guarantee(_length <= _allocated_length,
            err_msg("invariant: _length: %u _allocated_length: %u",
                    _length, _allocated_length));
  guarantee(_allocated_length <= _max_length,
            err_msg("invariant: _allocated_length: %u _max_length: %u",
                    _allocated_length, _max_length));
  guarantee(_next_search_index <= _length,
            err_msg("invariant: _next_search_index: %u _length: %u",
                    _next_search_index, _length));

  HeapWord* prev_end = _heap_bottom;
  for (uint i = 0; i < _allocated_length; i += 1) {
    HeapRegion* hr = _regions[i];
    guarantee(hr != NULL, err_msg("invariant: i: %u", i));
    guarantee(hr->bottom() == prev_end,
              err_msg("invariant i: %u "HR_FORMAT" prev_end: "PTR_FORMAT,
                      i, HR_FORMAT_PARAMS(hr), p2i(prev_end)));
    guarantee(hr->hrs_index() == i,
              err_msg("invariant: i: %u hrs_index(): %u", i, hr->hrs_index()));
    if (i < _length) {
      // Asserts will fire if i is >= _length
      HeapWord* addr = hr->bottom();
      guarantee(addr_to_region(addr) == hr, "sanity");
      guarantee(addr_to_region_unsafe(addr) == hr, "sanity");
    } else {
      guarantee(hr->is_empty(), "sanity");
      guarantee(!hr->isHumongous(), "sanity");
      // using assert instead of guarantee here since containing_set()
      // is only available in non-product builds.
      assert(hr->containing_set() == NULL, "sanity");
    }
    if (hr->startsHumongous()) {
      prev_end = hr->orig_end();
    } else {
      prev_end = hr->end();
    }
  }
  for (uint i = _allocated_length; i < _max_length; i += 1) {
    guarantee(_regions[i] == NULL, err_msg("invariant i: %u", i));
  }
}
#endif // PRODUCT
