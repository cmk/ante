import webapp2
import json
import numpy
from ante.wavelet import swt

class MainPage(webapp2.RequestHandler):
    def get(self):
        self.response.headers['Content-Type'] = 'application/json'
        arg_numbers = json.loads(self.request.get('timeseries'))
        num_levels = int(self.request.get('num_levels'))
        filter = ''
        try:
            filter = self.request.get('filter')
        except:
            pass
        if not filter:
            filter = 'd6'
        num_wavelet_numbers = pow(2, num_levels)
        x = numpy.zeros(num_wavelet_numbers)
        num_arg_numbers = min(len(arg_numbers), num_wavelet_numbers)
        last_number = num_wavelet_numbers - num_arg_numbers
        x[last_number:] = arg_numbers[len(arg_numbers) - num_arg_numbers:]
        x[:last_number] = x[last_number]
        wa, sc = swt.swt(x, wtf = filter, nlevels = num_levels)
        self.response.write(str(wa.tolist()))

application = webapp2.WSGIApplication([('/', MainPage)], debug=True)


