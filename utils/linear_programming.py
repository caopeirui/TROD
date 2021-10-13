'''
min_{x_0,x_1} -x_0 + 4 x_1

s.b. -3x_0 + x_1 \leq 6 \\
     -x_0 - 2x_1 \geq -4 \\
     x_1 \geq -3

from scipy.optimize import linprog
c = [-1, 4]
A = [[-3, 1], [1, 2]]
b = [6, 4]
x0_bounds = (None, None)
x1_bounds = (-3, None)
res = linprog(c, A_ub=A, b_ub=b, bounds=[x0_bounds, x1_bounds])
print(res)
'''

from scipy.optimize import linprog

class LinearProgramming():
    """
    Desc:  main demo  
            add_var()   
            add_constraint()  
    """
    def __init__(self):
        self._var_dict = {}
        self._var_num = 0
        self._c = []
        self._A_ub = []
        self._b_ub = []
        self._A_eq = []
        self._b_eq = []
        self._bounds_list = []


    def add_var(self, name : str, lb = None, ub = None):
        self._var_dict[name] = self._var_num
        self._var_num += 1
        self._bounds_list.append((lb, ub))


    def add_constraint(self, handwritten_str):
        """
        Desc:     handwritten_str   +   + 
                -3*x0 ( * - )
               
               : -3*x0+1*x1<=6
                  -1*x0 + -2*x1 >= -4
                   
        """
        handwritten_str = handwritten_str.replace(' ', '')
        if '<=' in handwritten_str:
            left, right = handwritten_str.split('<=')
            
            self._b_ub.append(float(right))

            #     list   A_ub
            #   _bounds_list  
            items = left.split('+')
            tmp_A_ub = [0 for _ in range(self._var_num)]
            for it in items:
                if '*' not in it:
                    #  1 
                    index = self._var_dict[it]
                    tmp_A_ub[index] = 1
                else:
                    coe, var_name = it.split('*')
                    index = self._var_dict[var_name]
                    tmp_A_ub[index] = float(coe)
            self._A_ub.append(tmp_A_ub)
        elif '>=' in handwritten_str:
            left, right = handwritten_str.split('>=')
            self._b_ub.append(-float(right))  #  
            items = left.split('+')
            tmp_A_ub = [0 for _ in range(self._var_num)]
            for it in items:
                if '*' not in it:
                    #  1 
                    index = self._var_dict[it]
                    tmp_A_ub[index] = -1    #  
                else:
                    coe, var_name = it.split('*')
                    index = self._var_dict[var_name]
                    tmp_A_ub[index] = -float(coe)  #  
            self._A_ub.append(tmp_A_ub)
        elif '==' in handwritten_str:
            left, right = handwritten_str.split('==')
            self._b_eq.append(float(right))
            items = left.split('+')
            tmp_A_eq = [0 for _ in range(self._var_num)]
            for it in items:
                if '*' not in it:
                    #  1 
                    index = self._var_dict[it]
                    tmp_A_eq[index] = 1
                else:
                    coe, var_name = it.split('*')
                    index = self._var_dict[var_name]
                    tmp_A_eq[index] = float(coe)            
            self._A_eq.append(tmp_A_eq)
        else:
            print('handwritten_str error')
            exit()


    def add_objective(self, handwritten_str):
        """
        Desc: minimize objective
                  handwritten_str   +   + 
                -3*x0 ( * - )
        """
        handwritten_str = handwritten_str.replace(' ', '')
        items = handwritten_str.split('+')
        tmp_c = [0 for _ in range(self._var_num)] 
        for it in items:
            if '*' not in it:
                #  1 
                index = self._var_dict[it]
                tmp_c[index] = 1
            else:
                coe, var_name = it.split('*')
                index = self._var_dict[var_name]
                tmp_c[index] = float(coe)
        self._c = tmp_c


    def solve(self, method='interior-point', callback=None,
              options=None, x0=None):
        """
        Desc: If you need greater accuracy, try method = 'revised simplex'.
        """
        self._A_ub = None if self._A_ub == [] else self._A_ub
        self._b_ub = None if self._b_ub == [] else self._b_ub
        self._A_eq = None if self._A_eq == [] else self._A_eq
        self._b_eq = None if self._b_eq == [] else self._b_eq
        res = linprog(
            c = self._c, A_ub = self._A_ub, b_ub = self._b_ub,
            A_eq = self._A_eq, b_eq = self._b_eq,
            bounds = self._bounds_list,
            method = method, callback = callback,
            options = options, x0 = x0
        )
        var_solve = {}
        for var in self._var_dict.keys():
            var_solve[var] = res.x[self._var_dict[var]]
        return var_solve


if __name__ == "__main__":
    lp = LinearProgramming()
    lp.add_var('x0', None, None)
    lp.add_var('x1', -3, None)
    # print(lp._var_dict)
    # print(lp._var_num)
    lp.add_constraint('-1*x0 + -2*x1 >= -4')
    lp.add_constraint('-3*x0+1*x1<=6')
    # print(lp._A_ub)
    lp.add_objective('4*x1 + -1*x0')
    res = lp.solve()
    print(res)
