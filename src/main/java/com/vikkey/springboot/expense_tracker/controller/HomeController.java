package com.vikkey.springboot.expense_tracker.controller;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.vikkey.springboot.expense_tracker.dao.ICostAndDateQuery;
import com.vikkey.springboot.expense_tracker.dao.ICostAndMonthQuery;
import com.vikkey.springboot.expense_tracker.dao.ICostAndYearQuery;
import com.vikkey.springboot.expense_tracker.entity.ExpenseStorage;
import com.vikkey.springboot.expense_tracker.service.ExpenseService;
import com.vikkey.springboot.expense_tracker.support.ExpenseRecorder;

@Controller
public class HomeController {

	@Autowired
	private ExpenseService theExpenseService;

	/*
	 * Function: Show the homepage and get the model to add attribute to thymeleaf template.
	 * 
	 */
	@GetMapping("/")
	public String showHome(Model theModel) {
		
		int[] costList = new int[12];
		
		// get the list of cost and month from the database.
		List<ICostAndMonthQuery> costMonthList = theExpenseService.getCostByMonthList();

		// create the list of cost in the order of 12 months.
		for(ICostAndMonthQuery entry: costMonthList) {
			costList[Integer.parseInt(entry.getMonth())-1] = entry.getCost();
		}
		
		ExpenseRecorder theExpenseRecorder = new ExpenseRecorder();
		
		// add the attributes in the model
		theModel.addAttribute("currentExpense", costList[Integer.parseInt(theExpenseRecorder.getSelectedMonth())-1]);
		theModel.addAttribute("costList", costList);
		return "home-page";
	}

	/*
	 * Function: To show the current expense page by retrieving the data from the database
	 * 
	 * 
	 */
	@GetMapping("/current-expense")
	public String showCurrentExpense(Model theModel) {

		ExpenseRecorder theExpenseRecorder = new ExpenseRecorder();
		
		// create the list of all the attributes based on the current month.
		List<ExpenseStorage> theExpenseList = theExpenseService.getListByMonth(theExpenseRecorder.getSelectedMonth(),
				theExpenseRecorder.getSelectedYear());

		// add the attributes.
		theModel.addAttribute("expenselist", theExpenseList);
		theModel.addAttribute("expenseRecorder", new ExpenseRecorder());

		return "current-expense";
	}
	
	/*
	 * Function: Get the current expense based on the selected month and year.
	 * 
	 */
	@PostMapping("/current-expense-changed")
	public String showCurrentExpenseChanged(@ModelAttribute("expenseRecorder") 
	ExpenseRecorder theExpenseRecorder, 
	Model theModel) {
		
		// get the data based on the selected year and month.
		List<ExpenseStorage> theExpenseList = theExpenseService.getListByMonth(
																		theExpenseRecorder.getSelectedMonth(),
																		theExpenseRecorder.getSelectedYear());

		theModel.addAttribute("expenselist", theExpenseList);
		return "current-expense";
	}

	/*
	 * Function: Show the empty add expense form.
	 * 
	 */
	@GetMapping("/expense-form")
	public String showAddExpenseForm(Model theModel) {
		theModel.addAttribute("expenseStorage", new ExpenseStorage());		
		return "expense-form";
	}

	/*
	 * Function: Show the expense form to update and all fields are set with default data inserted before.
	 * 
	 */
	@GetMapping("/edit-expense-form")
	public String showUpdateExpenseForm(@ModelAttribute("expenseStorage") ExpenseStorage theExpenseStorage, Model theModel) {
		
		// retrieve the data from the database
		Optional<ExpenseStorage> retreivedExpense = theExpenseService.findById(theExpenseStorage.getId());
														retreivedExpense.get().setDateInSpecificFormat("MM/dd/yyyy");	

		// set the data in the expense form
		theModel.addAttribute("expenseStorage", retreivedExpense.get());
		return "expense-form";
	}

	/*
	 * Function: To delete the expense record from the list
	 * 
	 */
	@GetMapping("/delete-expense")
	public String deleteExpenseForm(@ModelAttribute("expenseStorage") ExpenseStorage theExpenseStorage) {
		// get the id and delete it from database
		theExpenseService.deleteById(theExpenseStorage.getId());
		
		// once deleted redirect to current-expense form with updated data.
		return "redirect:/current-expense";
	}

	/*
	 * Function: Save the expense form and before saving it do the prechecks and validations
	 * 
	 */
	@PostMapping("/save-expense")
	public String saveExpense(@Valid 
			@ModelAttribute("expenseStorage") ExpenseStorage theExpenseStorage,
			BindingResult theBindingResult,
			HttpServletRequest request) {

		// check for the validation errors where spring provides built-in validation functionality
		if(theBindingResult.hasErrors()) {
			return "expense-form";
		}

		// defined the custom validation to check whether user selected the category or not.
		if(theExpenseStorage.getCategory() == null || theExpenseStorage.getCategory().contains("Category")) {
			theExpenseStorage.setErrorMsg("You must select the category.");
			return "expense-form";
		}

		// change the date as per database format in order store in the database
		theExpenseStorage.setDateInSpecificFormat("yyyy-MM-dd");

		// check in the database whether the similar data entry is present or not.
		if(theExpenseService.expenseEntryIsPresent(theExpenseStorage)) {
			
			// if data is already present than change the data in the regular format.
			theExpenseStorage.setDateInSpecificFormat("MM/dd/yyyy");
			theExpenseStorage.setErrorMsg("The given data already exist.");
			return "expense-form";
		}

		// check whether the request is invoked for updated data or new data.
		String action = request.getParameter("button");

		// save the updated data
		if(action.compareTo("saveUpdated") == 0) {
			ExpenseStorage expenseToUpdate = theExpenseService.retreiveForUpdate(theExpenseStorage.getId());
			expenseToUpdate.setOwner(theExpenseStorage.getOwner());
			expenseToUpdate.setProduct(theExpenseStorage.getProduct());
			expenseToUpdate.setCategory(theExpenseStorage.getCategory());
			expenseToUpdate.setCost(theExpenseStorage.getCost());
			expenseToUpdate.setDate(theExpenseStorage.getDate());
			theExpenseService.save(theExpenseStorage);
			return "redirect:/current-expense";
		}

		// save the new data and show current-expense
		theExpenseService.save(theExpenseStorage);
		if(action.compareTo("saveNew") == 0) {
			return "redirect:/current-expense";
		}

		// save the new data and show expense-form in order to add new entry.
		return "redirect:/expense-form";
	} 

	/*
	 * Function: to convert trim input strings remove leading and trailing whitespace 
	 * 			 resolve issue for our validation
	 */
	@InitBinder
	public void initBinder(WebDataBinder webDataBinder)  {
		StringTrimmerEditor stringTrimmerEditor = new StringTrimmerEditor(true);
		webDataBinder.registerCustomEditor(String.class, stringTrimmerEditor);		
	}

	/*
	 * Function: Show the reportcard based on the selected type and generate the template dynamically
	 * 			 based on the request.
	 * 
	 */
	@RequestMapping(value= {"/daily", "/monthly", "/yearly"})
	public String showReportcard(@ModelAttribute("expenseRecorder") 
	ExpenseRecorder theExpenseRecorder, 
	Model theModel,
	HttpServletRequest request) {
		String reportcardType = null;
		int[] costList = null;

		// get the requested URI and based on that generate the template.
		switch(request.getRequestURI()) {
		case "/daily":
			reportcardType="daily";
			
			// get the list of cost and date from the database.
			List<ICostAndDateQuery>  costDateList = theExpenseService.getCostByDateList(theExpenseRecorder.getSelectedMonth(), 
					theExpenseRecorder.getSelectedYear());

			// get the no. of days from the selected month.
			YearMonth yearMonthObject = YearMonth.of(Integer.parseInt(theExpenseRecorder.getSelectedYear()), 
					Integer.parseInt(theExpenseRecorder.getSelectedMonth()));

			int daysInMonth = yearMonthObject.lengthOfMonth();

			// day starts from 1.
			costList = new int[daysInMonth+1];

			// strip the date 'dd' from dateformat MM/dd/yyyy and convert to integer which
			// will be the index
			for(ICostAndDateQuery entry: costDateList) {
				costList[Integer.parseInt(entry.getDate().substring(3, 5))] = entry.getCost();
			}

			theModel.addAttribute("daysInMonth", daysInMonth);						
			break;

		case "/monthly":
			reportcardType="monthly";
			costList = new int[13];
			
			// get the list by month and cost
			List<ICostAndMonthQuery> costMonthList = theExpenseService.getCostByMonthList();

			// generate the costlist in the proper order of the month.
			for(ICostAndMonthQuery entry: costMonthList) {
				costList[Integer.parseInt(entry.getMonth())] = entry.getCost();
			}			
			break;

		case "/yearly":
			reportcardType="yearly";
			int costListSize = theExpenseRecorder.getYearList().size() - 1;
			costList = new int[costListSize];
			
			// get the cost by year
			List<ICostAndYearQuery> costYearList = theExpenseService.getCostByYearList();
			
			for(int indx = 0; indx < costListSize; indx++) {
				
				// check the size of the data we retrieved from the database
				// compare the year and get the mapped cost and fill in the array.
				if(costYearList.size() > indx && costYearList.get(indx).getYear().equals(theExpenseRecorder.getYearList().get(indx))) {
					costList[indx] = costYearList.get(indx).getCost();					
				}
				else {
					// else fill 0.
					costList[indx] = 0;
				}
			}

			theModel.addAttribute("yearList", theExpenseRecorder.getYearList());
			break;
		}

		theModel.addAttribute("costList", costList);
		theModel.addAttribute("reportcardType", reportcardType);
		return "reportcard";
	}
}
