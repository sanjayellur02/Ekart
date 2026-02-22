package com.example.ekart.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import com.example.ekart.dto.Address;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;

import com.example.ekart.dto.Cart;
import com.example.ekart.dto.Customer;
import com.example.ekart.dto.Item;
import com.example.ekart.dto.Product;
import com.example.ekart.dto.Review;
import com.example.ekart.dto.Order; 
import com.example.ekart.helper.AES;
import com.example.ekart.repository.AddressRepository;
import com.example.ekart.repository.CustomerRepository;
import com.example.ekart.repository.ItemRepository;
import com.example.ekart.repository.OrderRepository;
import com.example.ekart.repository.ProductRepository;
import com.example.ekart.repository.ReviewRepository;

import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;


@Service
@Transactional
public class CustomerService {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private com.example.ekart.helper.EmailSender emailSender;

    @Autowired ReviewRepository reviewRepository;

    @Autowired
private AddressRepository addressRepository;



    // ---------------- REGISTER ----------------
    public String loadRegistration(ModelMap map, Customer customer) {
        map.put("customer", customer);
        return "customer-register.html";
    }

    public String registration(Customer customer, BindingResult result, HttpSession session) {

        if (!customer.getPassword().equals(customer.getConfirmPassword()))
            result.rejectValue("confirmPassword", "error.confirmPassword",
                    "* Password and Confirm Password Should Match");

        if (customerRepository.existsByEmail(customer.getEmail()))
            result.rejectValue("email", "error.email", "* Email Already Exists");

        if (customerRepository.existsByMobile(customer.getMobile()))
            result.rejectValue("mobile", "error.mobile", "* Mobile Number Already Exists");

        if (result.hasErrors())
            return "customer-register.html";

        int otp = new Random().nextInt(100000, 1000000);
        customer.setOtp(otp);
        customer.setPassword(AES.encrypt(customer.getPassword()));
        customerRepository.save(customer);
        System.out.println("Customer OTP: " + customer.getOtp());

        session.setAttribute("success", "Otp Sent Successfully");
        return "redirect:/customer/otp/" + customer.getId();
    }

    public String verifyOtp(int id, int otp, HttpSession session) {
        Customer customer = customerRepository.findById(id).orElseThrow();

        if (customer.getOtp() == otp) {
            customer.setVerified(true);
            customerRepository.save(customer);
            session.setAttribute("success", "Customer Account Created Successfully");
            return "redirect:/";
        }

        session.setAttribute("failure", "OTP Mismatch");
        return "redirect:/customer/otp/" + customer.getId();
    }

    // ---------------- LOGIN ----------------
    public String login(String email, String password, HttpSession session) {

        Customer customer = customerRepository.findByEmail(email);

        if (customer == null) {
            session.setAttribute("failure", "Invalid Email");
            return "redirect:/customer/login";
        }

        if (!AES.decrypt(customer.getPassword()).equals(password)) {
            session.setAttribute("failure", "Invalid Password");
            return "redirect:/customer/login";
        }

        if (!customer.isVerified()) {
            session.setAttribute("failure", "Verify Email First");
            return "redirect:/customer/login";
        }

        // Ensure cart exists
        if (customer.getCart() == null) {
            Cart cart = new Cart();
            cart.setItems(new ArrayList<>());
            customer.setCart(cart);
            customerRepository.save(customer);
        }

        session.setAttribute("customer", customer);
        session.setAttribute("success", "Login Successful");
        return "redirect:/customer/home";
    }

    public String loadCustomerHome(HttpSession session) {
        if (session.getAttribute("customer") != null)
            return "customer-home.html";

        session.setAttribute("failure", "Login First");
        return "redirect:/customer/login";
    }

    // ---------------- VIEW PRODUCTS ----------------
    public String viewProducts(HttpSession session, ModelMap map) {

        if (session.getAttribute("customer") == null)
            return "redirect:/customer/login";

        List<Product> products = productRepository.findByApprovedTrue();

        if (products.isEmpty()) {
            session.setAttribute("failure", "No Products Available");
            return "redirect:/customer/home";
        }

        map.put("products", products);
        return "customer-view-products.html";
    }

    // ---------------- SEARCH ----------------
    public String searchProducts(HttpSession session) {
        return "search.html";
    }

    public String search(String query, HttpSession session, ModelMap map) {
        HashSet<Product> products = new HashSet<>();
        products.addAll(productRepository.findByNameContainingIgnoreCase(query));
        products.addAll(productRepository.findByDescriptionContainingIgnoreCase(query));
        products.addAll(productRepository.findByCategoryContainingIgnoreCase(query));

        map.put("products", products);
        map.put("query", query);
        return "search.html";
    }

    // ---------------- ADD TO CART ----------------
    public String addToCart(int id, HttpSession session) {

        Customer sessionCustomer = (Customer) session.getAttribute("customer");

        if (sessionCustomer == null) {
            session.setAttribute("failure", "Session Expired, Login Again");
            return "redirect:/customer/login";
        }

        Customer customer = customerRepository.findById(sessionCustomer.getId()).orElseThrow();
        Product product = productRepository.findById(id).orElseThrow();

        Cart cart = customer.getCart();
        if (cart.getItems() == null)
            cart.setItems(new ArrayList<>());

        boolean exists = cart.getItems()
                .stream()
                .anyMatch(i -> i.getName().equals(product.getName()));

        if (exists) {
            session.setAttribute("failure", "Product already in cart");
            return "redirect:/customer/home";
        }

        Item item = new Item();
        item.setName(product.getName());
        item.setCategory(product.getCategory());
        item.setDescription(product.getDescription());
        item.setImageLink(product.getImageLink());
        item.setPrice(product.getPrice());
        item.setQuantity(1);

        item.setCart(cart);
        cart.getItems().add(item);

        product.setStock(product.getStock() - 1);

        itemRepository.save(item);
        productRepository.save(product);
        customerRepository.save(customer);

        session.setAttribute("success", "Added to cart");
        return "redirect:/customer/home";
    }

    // ---------------- VIEW CART ----------------
    public String viewCart(HttpSession session, ModelMap map) {
        Customer customer = (Customer) session.getAttribute("customer");
        if (customer == null) {
            session.setAttribute("failure", "Login First");
            return "redirect:/customer/login";
        }
        
        customer = customerRepository.findById(customer.getId()).orElseThrow();
        List<Item> items = customer.getCart().getItems();
        
        double totalPrice = 0;
        if (items != null) {
             for (Item item : items) {
                totalPrice += item.getPrice();
            }
        }

        map.put("items", items);
        map.put("totalPrice", totalPrice);
        
        return "view-cart.html";
    }

    // ---------------- INCREASE QUANTITY ----------------
    public String increase(int id, HttpSession session) {
        Item item = itemRepository.findById(id).orElseThrow();
        Product product = productRepository.findByNameContainingIgnoreCase(item.getName()).get(0);

        item.setQuantity(item.getQuantity() + 1);
        item.setPrice(item.getPrice() + product.getPrice());

        product.setStock(product.getStock() - 1);

        itemRepository.save(item);
        productRepository.save(product);

        return "redirect:/view-cart";
    }

    // ---------------- DECREASE QUANTITY ----------------
    public String decrease(int id, HttpSession session) {
        Item item = itemRepository.findById(id).orElseThrow();
        List<Product> products = productRepository.findByNameContainingIgnoreCase(item.getName());

        if (products.isEmpty()) {
            item.getCart().getItems().removeIf(i -> i.getId() == item.getId());
            itemRepository.delete(item);
            session.setAttribute("failure", "This product is no longer available.");
            return "redirect:/view-cart";
        }

        Product product = products.get(0);

        if (item.getQuantity() > 1) {
            item.setQuantity(item.getQuantity() - 1);
            item.setPrice(item.getPrice() - product.getPrice());
            itemRepository.save(item);
        } else {
            item.getCart().getItems().removeIf(i -> i.getId() == item.getId());
            itemRepository.delete(item);
        }

        product.setStock(product.getStock() + 1);
        productRepository.save(product);

        return "redirect:/view-cart";
    }

    // ---------------- REMOVE FROM CART ----------------
    @Transactional
public String removeFromCart(int id, HttpSession session) {
    // 1. Fetch the Item
    Item item = itemRepository.findById(id).orElseThrow();
    
    // 2. Restore Stock
    List<Product> products = productRepository.findByNameContainingIgnoreCase(item.getName());
    if (!products.isEmpty()) {
        Product product = products.get(0);
        product.setStock(product.getStock() + item.getQuantity());
        productRepository.save(product);
    }

    // 🔥 DELETE THIS: itemRepository.delete(item); 
    // 🔥 REPLACE WITH THIS:
    item.setCart(null); 
    itemRepository.save(item); 

    // 3. Clear from the Session list
    Customer sessionCustomer = (Customer) session.getAttribute("customer");
    Customer customer = customerRepository.findById(sessionCustomer.getId()).orElseThrow();
    customer.getCart().getItems().removeIf(i -> i.getId() == id);
    customerRepository.save(customer);

    session.setAttribute("success", "Item Removed from Cart");
    return "redirect:/view-cart";
}

    // ---------------- PAYMENT PAGE ----------------
    public String payment(HttpSession session, ModelMap map) {
    Customer customer = (Customer) session.getAttribute("customer");
    if (customer == null) {
        session.setAttribute("failure", "Login First");
        return "redirect:/customer/login";
    }
    
    customer = customerRepository.findById(customer.getId()).orElseThrow();
    List<Item> items = customer.getCart().getItems();
    
    // 🔥 ADD THIS SECURITY CHECK:
    if (items == null || items.isEmpty()) {
        session.setAttribute("failure", "Your cart is empty! Add products before paying.");
        return "redirect:/view-cart";
    }
    
    double amount = 0;
    for (Item item : items) {
        amount += item.getPrice();
    }
    
    map.put("amount", amount);
    map.put("customer", customer);
    
    // Add recommendations logic here as well if needed
    
    return "payment.html";
}

    // ---------------- PAYMENT SUCCESS (CLONING LOGIC) ----------------
    public String paymentSuccess(Order order, HttpSession session) {
        Customer sessionCustomer = (Customer) session.getAttribute("customer");
        Customer customer = customerRepository.findById(sessionCustomer.getId()).orElseThrow();
        
        order.setCustomer(customer);
        order.setOrderDate(java.time.LocalDateTime.now());
        
        List<Item> orderItems = new ArrayList<>();
        // Clone items to give them unique IDs for Order history
        for (Item cartItem : customer.getCart().getItems()) {
            Item newItem = new Item();
            newItem.setName(cartItem.getName());
            newItem.setPrice(cartItem.getPrice());
            newItem.setQuantity(cartItem.getQuantity());
            newItem.setCategory(cartItem.getCategory());
            newItem.setDescription(cartItem.getDescription());
            newItem.setImageLink(cartItem.getImageLink());
            orderItems.add(newItem);
        }
        
        order.setItems(orderItems);
        
        // Clear the cart. orphanRemoval deletes old IDs, clones remain in Order.
        customer.getCart().getItems().clear();
        
        orderRepository.save(order);
        customerRepository.save(customer);

        session.setAttribute("success", "Order Placed Successfully!");
        return "redirect:/customer/home";
    }

    // ---------------- VIEW ORDERS ----------------
    public String viewOrders(HttpSession session, ModelMap map) {
        Customer customer = (Customer) session.getAttribute("customer");
        if (customer == null) {
            session.setAttribute("failure", "Login First");
            return "redirect:/customer/login";
        }
        
        List<Order> orders = orderRepository.findByCustomer(customer);
        map.put("orders", orders);
        return "view-orders.html";
    }

    @Transactional
public String cancelOrder(int id, HttpSession session) {
    Customer sessionCustomer = (Customer) session.getAttribute("customer");
    if (sessionCustomer == null) {
        session.setAttribute("failure", "Login First");
        return "redirect:/customer/login";
    }

    // 1. Find the order first
    Order order = orderRepository.findById(id).orElseThrow();
    
    // 2. Save the data we need for the email BEFORE we delete the order
    double amount = order.getAmount();
    int orderId = order.getId();

    // 3. Restore stock for each item
    for (Item item : order.getItems()) {
        List<Product> products = productRepository.findByNameContainingIgnoreCase(item.getName());
        if (!products.isEmpty()) {
            Product product = products.get(0);
            product.setStock(product.getStock() + item.getQuantity());
            productRepository.save(product);
        }
    }

    // 4. Send the cancellation email
    try {
        emailSender.sendOrderCancellation(sessionCustomer, amount, orderId);
    } catch (Exception e) {
        System.err.println("Cancellation email failed to send.");
    }

    // 5. Delete the order from the database
    orderRepository.delete(order);

    session.setAttribute("success", "Order #" + orderId + " Cancelled Successfully");
    return "redirect:/view-orders";
}

public void addReview(int productId, int rating, String comment, HttpSession session) {
    Customer customer = (Customer) session.getAttribute("customer");
    Product product = productRepository.findById(productId).orElseThrow();

    Review review = new Review();
    review.setRating(rating);
    review.setComment(comment);
    review.setCustomerName(customer.getName());
    review.setProduct(product);

    reviewRepository.save(review);
}
public List<Product> getProductsByCategory(String category, String currentName) {
    List<Product> list = productRepository.findByCategoryAndApprovedTrue(category);
    
    // 🔥 This removes the "Biscuit" from the recommendation list
    list.removeIf(p -> p.getName().equalsIgnoreCase(currentName));
    
    // Limit to 2 items so it fits nicely in your col-6 layout
    return list.size() > 2 ? list.subList(0, 2) : list;
}

// Method to load the address page with fresh data
public String loadAddressPage(HttpSession session, ModelMap map) {
    Customer sessionCustomer = (Customer) session.getAttribute("customer");
    if (sessionCustomer == null) return "redirect:/customer/login";

    Customer customer = customerRepository.findById(sessionCustomer.getId()).orElseThrow();
    map.put("customer", customer); // This now includes the 'addresses' list
    return "address-page.html";
}

// Method to save a NEW address to the list
public String saveAddress(String addressDetails, HttpSession session) {
    Customer sessionCustomer = (Customer) session.getAttribute("customer");
    Customer customer = customerRepository.findById(sessionCustomer.getId()).orElseThrow();

    Address newAddress = new Address();
    newAddress.setDetails(addressDetails);
    newAddress.setCustomer(customer);
    
    customer.getAddresses().add(newAddress);
    customerRepository.save(customer);

    return "redirect:/customer/address"; 
}

// Method to delete a specific address
public String deleteAddress(int id, HttpSession session) {
    addressRepository.deleteById(id);
    return "redirect:/customer/address";
}

}