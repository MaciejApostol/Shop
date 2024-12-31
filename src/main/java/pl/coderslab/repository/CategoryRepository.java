package pl.coderslab.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import pl.coderslab.entity.Category;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllChildrenByParentCategory(Category parent);

    List<Category> findAllByParentCategoryIsNull();

    Category findByNameAndParentCategory(String name, Category parent);

    Category findByPath(String path);

    Category findByName(String name);

    List<Category> findByParentCategory(Category parent);

    @Query(value = "select * from category", nativeQuery = true)
    List<Category> findAllSQL();

    @Query(value = "select * from category where parents_path regexp concat('(?:^|-)',?1,'(?:-|$)');", nativeQuery = true)
    List<Category> findAllByParentCategoryId(String id);


}