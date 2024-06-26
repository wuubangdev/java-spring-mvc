package vn.hoidanit.laptopshop.controller.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

import vn.hoidanit.laptopshop.domain.Cart;
import vn.hoidanit.laptopshop.domain.CartDetail;
import vn.hoidanit.laptopshop.domain.Product;
import vn.hoidanit.laptopshop.domain.Product_;
import vn.hoidanit.laptopshop.domain.User;
import vn.hoidanit.laptopshop.domain.dto.ProductCriteriaDTO;
import vn.hoidanit.laptopshop.service.CartDetailService;
import vn.hoidanit.laptopshop.service.ProductService;
import vn.hoidanit.laptopshop.service.UserService;

import org.springframework.web.bind.annotation.PostMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class IteamController {

    private final ProductService productService;
    private final CartDetailService cartDetailService;

    public IteamController(ProductService productService, UserService userService,
            CartDetailService cartDetailService) {
        this.productService = productService;
        this.cartDetailService = cartDetailService;
    }

    @GetMapping("/products")
    public String getProductsPage(Model model, ProductCriteriaDTO productCriteriaDTO, HttpServletRequest request) {
        int page = 1;
        try {
            if (productCriteriaDTO.getPage().isPresent()) {
                page = Integer.parseInt(productCriteriaDTO.getPage().get());
            }
        } catch (Exception e) {
        }
        Pageable pageable = PageRequest.of(page - 1, 6);
        if (productCriteriaDTO.getSort() != null && productCriteriaDTO.getSort().isPresent()) {
            String sort = productCriteriaDTO.getSort().get();
            if (sort.equals("increment")) {
                pageable = PageRequest.of(page - 1, 6, Sort.by(Product_.PRICE).ascending());
            } else if (sort.equals("decrement")) {
                pageable = PageRequest.of(page - 1, 6, Sort.by(Product_.PRICE).descending());
            }
        }

        Page<Product> pageProduct = this.productService.fetchProductWithSpec(pageable, productCriteriaDTO);
        List<Product> products = pageProduct.getContent().size() > 0 ? pageProduct.getContent() : new ArrayList<>();

        String qs = request.getQueryString();
        if (qs != null && !qs.isBlank()) {
            qs = qs.replace("page=" + page, "");
        }
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", pageProduct.getTotalPages());
        model.addAttribute("products", products);
        model.addAttribute("queryString", qs);
        return "client/product/show";
    }

    @GetMapping("/product/{id}")
    public String getMethodName(Model model, @PathVariable long id) {
        Product product = this.productService.getProductById(id);
        model.addAttribute("product", product);
        model.addAttribute("cartDetail", new CartDetail());
        return "client/product/detail";
    }

    @PostMapping("/add-product-to-cart/{id}")
    public String addProductToCart(@PathVariable long id, HttpServletRequest request) {
        HttpSession session = request.getSession();
        long productId = id;
        long quantity = 1;
        String email = (String) session.getAttribute("email");
        this.productService.handleAddProductToCart(email, productId, quantity, session);
        return "redirect:/";
    }

    @PostMapping("/add-multi-product-to-cart")
    public String addMultiProductToCart(HttpServletRequest request,
            @ModelAttribute("cartDetail") CartDetail cartDetail) {
        HttpSession session = request.getSession();
        String email = (String) session.getAttribute("email");
        long productId = cartDetail.getProduct().getId();
        long quantity = cartDetail.getQuantity();
        this.productService.handleAddProductToCart(email, productId, quantity, session);
        return "redirect:/cart";
    }

    @GetMapping("/cart")
    public String getCartPage(Model model, HttpServletRequest request) {
        User currentUser = new User();
        HttpSession session = request.getSession();
        long id = (long) session.getAttribute("id");
        currentUser.setId(id);
        Cart cart = this.productService.fetchByUser(currentUser);
        List<CartDetail> cartDetails = new ArrayList<CartDetail>();
        double totalPrice = 0;
        if (cart != null) {
            cartDetails = cart.getCartDetail();
            for (CartDetail cd : cartDetails) {
                totalPrice += cd.getQuantity() * cd.getPrice();
            }
        }
        model.addAttribute("cartDetails", cartDetails);
        model.addAttribute("totalPrice", totalPrice);
        model.addAttribute("cart", cart);
        return "client/cart/show";
    }

    @PostMapping("/delete-cart-product/{id}")
    public String deleteCartDetail(@PathVariable long id, HttpServletRequest request) {
        HttpSession session = request.getSession();
        Optional<CartDetail> cartDetail = this.cartDetailService.fetchById(id);
        if (cartDetail.isPresent()) {
            this.productService.handleRemoveCartDetail(cartDetail.get(), session);
        }
        return "redirect:/cart";
    }

    @GetMapping("/checkout")
    public String getCheckOutPage(Model model, HttpServletRequest request) {
        User currentUser = new User();
        HttpSession session = request.getSession();
        long id = (long) session.getAttribute("id");
        currentUser.setId(id);
        Cart cart = this.productService.fetchByUser(currentUser);
        List<CartDetail> cartDetails = new ArrayList<CartDetail>();
        double totalPrice = 0;
        if (cart != null) {
            cartDetails = cart.getCartDetail();
            for (CartDetail cd : cartDetails) {
                totalPrice += cd.getQuantity() * cd.getPrice();
            }
        }
        model.addAttribute("cartDetails", cartDetails);
        model.addAttribute("totalPrice", totalPrice);
        model.addAttribute("cart", cart);
        return "client/cart/checkout";
    }

    @PostMapping("/confirm-checkout")
    public String confimCheckOut(@ModelAttribute("cart") Cart cart) {
        List<CartDetail> cartDetails = cart == null ? new ArrayList<CartDetail>() : cart.getCartDetail();
        this.productService.handleUpdateCartBeforeCheckOut(cartDetails);
        return "redirect:/checkout";
    }

    @PostMapping("/place-order")
    public String handlePlaceOrder(HttpServletRequest request,
            @RequestParam("receiverName") String receiverName,
            @RequestParam("receiverAddress") String receiverAddress,
            @RequestParam("receiverPhone") String receiverPhone,
            @RequestParam("totalPrice") double totalPrice) {
        HttpSession session = request.getSession();
        User currentUser = new User();
        long id = (long) session.getAttribute("id");
        currentUser.setId(id);
        this.productService.handlePlaceOrder(currentUser, session, receiverName, receiverAddress, receiverPhone,
                totalPrice);
        return "redirect:/thanks";
    }

    @GetMapping("/thanks")
    public String getThanksPage(Model model) {
        return "client/cart/thanks";
    }

}
