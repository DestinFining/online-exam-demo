package com.example.exam.favorite;
import com.example.exam.common.response.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/favorites")
public class FavoriteController {
    private final FavoriteService service; public FavoriteController(FavoriteService service){this.service=service;}
    @GetMapping @PreAuthorize("hasAuthority('favorite:manage')")
    public ApiResponse<?> page(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size,Authentication a){return ApiResponse.success(service.page(page,size,a.getName()));}
    @GetMapping("/{questionId}") @PreAuthorize("hasAuthority('favorite:manage')")
    public ApiResponse<?> exists(@PathVariable Long questionId,Authentication a){return ApiResponse.success(java.util.Map.of("favorite",service.exists(questionId,a.getName())));}
    @PostMapping("/{questionId}") @PreAuthorize("hasAuthority('favorite:manage')")
    public ApiResponse<?> add(@PathVariable Long questionId,Authentication a){service.add(questionId,a.getName());return ApiResponse.success("收藏成功",null);}
    @DeleteMapping("/{questionId}") @PreAuthorize("hasAuthority('favorite:manage')")
    public ApiResponse<?> remove(@PathVariable Long questionId,Authentication a){service.remove(questionId,a.getName());return ApiResponse.success("已取消收藏",null);}
}
