package com.fastaccess.github.usecase.issuesprs

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Input
import com.apollographql.apollo.rx2.Rx2Apollo
import com.fastaccess.data.model.PageInfoModel
import com.fastaccess.data.model.parcelable.FilterIssuesPrsModel
import com.fastaccess.data.persistence.models.MyIssuesPullsModel
import com.fastaccess.data.repository.LoginRepositoryProvider
import com.fastaccess.domain.usecase.base.BaseObservableUseCase
import github.SearchPullRequestsQuery
import io.reactivex.Observable
import javax.inject.Inject

/**
 * Created by Kosh on 20.01.19.
 */
class FilterPullRequestsUseCase @Inject constructor(
    private val loginRepository: LoginRepositoryProvider,
    private val apolloClient: ApolloClient
) : BaseObservableUseCase() {

    var cursor: Input<String?> = Input.absent()
    var filterModel = FilterIssuesPrsModel()

    override fun buildObservable(): Observable<Pair<PageInfoModel, List<MyIssuesPullsModel>>> = loginRepository.getLogin()
        .flatMapObservable { user ->
            return@flatMapObservable user.login?.let { login ->
                Rx2Apollo.from(apolloClient.query(SearchPullRequestsQuery(constructQuery(filterModel, login), cursor)))
                    .map { it.data()?.search }
                    .map { search ->
                        val list = search.nodes?.asSequence()?.mapNotNull { it.fragments.shortPullRequestRowItem }
                            ?.map {
                                MyIssuesPullsModel(it.id, it.databaseId, it.number, it.title,
                                    it.repository.nameWithOwner, it.comments.totalCount, it.state.name)
                            }
                            ?.toList() ?: arrayListOf()
                        val pageInfo = PageInfoModel(search.pageInfo.startCursor, search.pageInfo.endCursor,
                            search.pageInfo.isHasNextPage, search.pageInfo.isHasPreviousPage)
                        return@map Pair(pageInfo, list)
                    }
            } ?: Observable.empty<Pair<PageInfoModel, List<MyIssuesPullsModel>>>()
        }

    /**
     * Example: is:open is:pr author:k0shk0sh archived:false sort:created-desc
     */
    private fun constructQuery(model: FilterIssuesPrsModel, login: String): String {
        return StringBuilder()
            .append("is:${when (model.searchType) {
                FilterIssuesPrsModel.SearchType.OPEN -> "open"
                FilterIssuesPrsModel.SearchType.CLOSED -> "closed"
            }}")
            .append(" ")
            .append("is:pr")
            .append(" ")
            .append("${when (model.searchBy) {
                FilterIssuesPrsModel.SearchBy.ASSIGNED -> "assignee"
                FilterIssuesPrsModel.SearchBy.MENTIONED -> "mentions"
                FilterIssuesPrsModel.SearchBy.REVIEW_REQUESTS -> "review-requested"
                else -> "author"
            }}:$login")
            .append(" ")
            .append("archived:false")
            .append(" ")
            .append("sort:${when (model.searchSortBy) {
                FilterIssuesPrsModel.SearchSortBy.NEWEST -> "created-desc"
                FilterIssuesPrsModel.SearchSortBy.OLDEST -> "created-asc"
                FilterIssuesPrsModel.SearchSortBy.MOST_COMMENTED -> "comments-desc"
                FilterIssuesPrsModel.SearchSortBy.LEAST_COMMENTED -> "comments-asc"
                FilterIssuesPrsModel.SearchSortBy.RECENTLY_UPDATED -> "updated-desc"
                FilterIssuesPrsModel.SearchSortBy.LEAST_RECENTLY_UPDATED -> "updated-asc"
            }}")
            .append(" ")
            .apply {
                if (model.searchVisibility != FilterIssuesPrsModel.SearchVisibility.BOTH) {
                    append("is:${when (model.searchVisibility) {
                        FilterIssuesPrsModel.SearchVisibility.PUBLIC -> "public"
                        FilterIssuesPrsModel.SearchVisibility.PRIVATE -> "private"
                        else -> ""
                    }}")
                }
            }
            .toString()
    }
}