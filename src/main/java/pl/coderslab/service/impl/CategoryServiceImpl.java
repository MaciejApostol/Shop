package pl.coderslab.service.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.coderslab.dto.CategoryDto;
import pl.coderslab.entity.Category;
import pl.coderslab.repository.CategoryRepository;
import pl.coderslab.service.CategoryService;
import pl.coderslab.util.CustomMapper;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Stream;

@Service
public class CategoryServiceImpl implements CategoryService {
    private final CustomMapper customMapper;
    private final CategoryRepository categoryRepository;
    private final EntityManager entityManager;

    public CategoryServiceImpl(CustomMapper customMapper, CategoryRepository categoryRepository, EntityManager entityManager, EntityManager entityManager1) {
        this.customMapper = customMapper;
        this.categoryRepository = categoryRepository;
        this.entityManager = entityManager1;
    }

//    public CategoryServiceImpl(CategoryRepository categoryRepository) {
//        this.categoryRepository = categoryRepository;
//    }

//    public List<Object> createCategoriesHierarchy(List<Category> parents) {
//        List<Object> list = new ArrayList<>();
//        List<Object> childList = new ArrayList<>();
//        for (Category parent : parents) {
//            List<Category> children = findChildrenByParentCategory(parent);
//
//            if (!children.isEmpty()) {
//                List<Object> newParents = createCategoriesHierarchy(children);
//                List<Object> parentList = new ArrayList<>(List.of(parent.getName()));
//                parentList.addAll(newParents);
//                list.add(parentList);
//            }
//            else {
//                childList.add(parent.getName());
//            }
//        }
//        if(!childList.isEmpty()){
//            list.add(childList);
//        }
//        return list;
//    }

    private List<Category> sortCategories(List<Category> categories) {

        if (categories.size() > 1) {
            TreeMap<String, Category> treeMap = new TreeMap<>();
            for (Category category : categories) {
                treeMap.put(category.getName(), category);
            }
            return treeMap.values().stream().toList();
//            return categories.stream().sorted((Comparator.comparing(Category::getName))).toList();
        }
        return categories;
    }

    private final List<String> others = List.of("inne", "pozostałe");

    private List<Category> getSortedCategories(List<Category> categories) {
        List<Category> others = new ArrayList<>();
        List<Category> main = new ArrayList<>();
        for (Category category : categories) {
//            if (this.others.stream().anyMatch(prop -> category.getName().toLowerCase().startsWith(prop))) {
            if (category.getName().toLowerCase().startsWith("inne")) {
                others.add(category);
                continue;
            }
            main.add(category);
        }
        main = sortCategories(main);
        others = sortCategories(others);

        return Stream.concat(main.stream(), others.stream()).toList();
    }

    private List<CategoryDto> getCategoriesInHierarchy(List<Category> parents, Long parentId) {
        parents = getSortedCategories(parents);
        List<CategoryDto> list = new ArrayList<>();

        for (Category parent : parents) {
            CategoryDto parentDto = new CategoryDto();
            customMapper.mapCategoryToDto(parent, parentDto);

            if (parentId != null) {
                parentDto.setParentId(parentId);
            }

            List<Category> children = categoryRepository.findAllChildrenByParentCategory(parent);
            if (!children.isEmpty()) {
                parentDto.setChildren(getCategoriesInHierarchy(children, parent.getId()));
            }
            list.add(parentDto);
        }
        return list;
    }

    @Override
    public List<CategoryDto> getHierarchyMap() {
        List<Category> grandparents = categoryRepository.findAllByParentCategoryIsNull();
        return getCategoriesInHierarchy(grandparents, null);
    }

    @Override
    public List<Category> getParents(Category category) {
        List<Category> parents = new ArrayList<>(List.of(category));
        Category parent = category.getParentCategory();
        while (parent != null) {
            parent = findById(parent.getId());
            if (parent == null) {
                break;
            }
            parents.add(parent);
            parent = parent.getParentCategory();
        }
        if (parents.size() > 1) {
            Collections.reverse(parents);
        }
        return parents;
    }

    @Override
    public Category findById(Long id) {
        Optional<Category> byId = categoryRepository.findById(id);
        return byId.orElse(null);
    }

    @Override
    public Category findByName(String name) {
        return categoryRepository.findByName(name);
    }

    @Override
    public String[] splitPathAroundProduct(String path) {
        path = path.replace("/" + SEARCH_PATH + "/", "");
        return path.split("/" + PRODUCT_PATH + "/");
    }

    @Override
    public List<Category> findAllByParentCategory(Category category) {
        return categoryRepository.findAllChildrenByParentCategory(category);
    }

    @Override
    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    @Override
    public Category findByPath(String path) {
        return categoryRepository.findByPath(path);
    }

    @Override
    public String normalizeName(String unnormalized) {
        if (unnormalized == null) {
            return null;
        }
        String path = unnormalized.replaceAll(" ", "-").toLowerCase();
        return Normalizer.normalize(path, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[\\u0141-\\u0142]", "l");
    }

    @Override
    @Transactional
    public void save(Category category) {
        Category parent = category.getParentCategory();
        String name = category.getName();

        if (parent != null) {
            categoryRepository.findById(parent.getId())
                    .orElseThrow(() -> new Error("Parent category doesn't exist"));
        }

        Category byNameAndParentCategory = categoryRepository.findByNameAndParentCategory(name, parent);
        if (byNameAndParentCategory != null) {
            throw new Error("Category already exists");
        }
        String normalizedName = normalizeName(name);
        category.setPath(parent == null ? normalizedName : parent.getPath() + "/" + normalizedName);

//        categoryRepository.saveAndFlush(category);
//        if (parent != null) {
//            if (parent.getParentsPath() == null) {
//                category.setParentsPath(parent.getId() + "-" + category.getId());
//            } else {
//                category.setParentsPath(parent.getParentsPath() + "-" + category.getId());
//            }
//        }
        entityManager.persist(category);
        if (parent != null) {
            String parentPath;
            if (parent.getParentsPath() == null) {
                parentPath = String.valueOf(parent.getId());
            } else {
                parentPath = parent.getParentsPath();
            }
            category.setParentsPath(parentPath + "-" + category.getId());
        }
    }
}