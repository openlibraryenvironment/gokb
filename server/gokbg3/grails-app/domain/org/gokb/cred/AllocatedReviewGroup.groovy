package org.gokb.cred

import org.apache.commons.lang.builder.HashCodeBuilder

class AllocatedReviewGroup implements Serializable {

  CuratoryGroup group
  ReviewRequest review
  RefdataValue status

	boolean equals(other) {
		if (!(other instanceof AllocatedReviewGroup)) {
			return false
		}

		other.group?.id == group?.id &&
			other.review?.id == review?.id
	}

	int hashCode() {
		def builder = new HashCodeBuilder()
		if (review) builder.append(review.id)
		if (group) builder.append(group.id)
		builder.toHashCode()
	}

	static AllocatedReviewGroup get(long groupId, long reviewId) {
		find 'from AllocatedReviewGroup where group.id=:groupId and review.id=:reviewId',
			[groupId: groupId, reviewId: reviewId]
	}

	static AllocatedReviewGroup create(CuratoryGroup grp, ReviewRequest rr, boolean flush = false) {
		new AllocatedReviewGroup(group: grp, review: rr).save(flush: flush)
	}

	static boolean remove(CuratoryGroup group, ReviewRequest review, boolean flush = false) {
		AllocatedReviewGroup instance = AllocatedReviewGroup.findByGroupAndReview(group, review)
		if (!instance) {
			return false
		}

		instance.delete(flush: flush)
		true
	}

	static void removeAll(CuratoryGroup group) {
		executeUpdate 'DELETE FROM AllocatedReviewGroup WHERE group=:group', [group: group]
	}

	static void removeAll(ReviewRequest review) {
		executeUpdate 'DELETE FROM AllocatedReviewGroup WHERE review=:review', [review: review]
	}

	static mapping = {
		id composite: ['review', 'group']
		version false
	}

	static constraints = {
		group(nullable:false, blank:false)
    review(nullable:false, blank:false)
	}
}
