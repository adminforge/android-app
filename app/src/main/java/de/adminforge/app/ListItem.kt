package de.adminforge.app

sealed class ListItem {
    data class CategoryItem(val category: Category) : ListItem()
    data class ServiceItem(val service: Service) : ListItem()
}
